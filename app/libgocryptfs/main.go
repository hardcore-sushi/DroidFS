package main

import (
  "C"
  "crypto/cipher"
  "crypto/aes"
  "syscall"
  "strings"
  "bytes"
  "unsafe"
  "os"
  "io"
  "fmt"
  "path/filepath"
  "golang.org/x/sys/unix"

  "./gocryptfs_internal/cryptocore"
  "./gocryptfs_internal/stupidgcm"
  "./gocryptfs_internal/eme"
  "./gocryptfs_internal/nametransform"
  "./rewrites/syscallcompat"
  "./rewrites/configfile"
  "./rewrites/contentenc"
)

const (
  file_mode = uint32(0660)
  folder_mode = uint32(0770)
)

type Directory struct {
  fd int
  iv []byte
}

type File struct {
  fd *os.File
  path string
}

type SessionVars struct {
  root_cipher_dir string
  nameTransform *nametransform.NameTransform
  cryptoCore *cryptocore.CryptoCore
  contentEnc *contentenc.ContentEnc
  dirCache map[string]Directory
  file_handles map[int]File
  fileIDs map[int][]byte
}

var sessions map[int]SessionVars

func err_to_bool(e error) bool {
  if e == nil {
    return true
  }
  return false
}

func wipe(d []byte){
  for i := range d {
    d[i] = 0
  }
  d = nil
}

func clear_dirCache(sessionID int) {
  for k, _ := range sessions[sessionID].dirCache {
    delete(sessions[sessionID].dirCache, k)
  }
}

func openBackingDir(sessionID int, relPath string) (dirfd int, cName string, err error) {
  dirRelPath := nametransform.Dir(relPath)
  dir, ok := sessions[sessionID].dirCache[dirRelPath]
  if ok {
    // If relPath is empty, cName is ".".
    if relPath == "" {
    cache_dirfd, err := syscall.Dup(dir.fd)
    if err != nil {
      return -1, "", err
    }
			return cache_dirfd, ".", nil
		}
    name := filepath.Base(relPath)
		cName, err = sessions[sessionID].nameTransform.EncryptAndHashName(name, dir.iv)
		if err != nil {
			syscall.Close(dir.fd)
			return -1, "", err
		}
    cache_dirfd, err := syscall.Dup(dir.fd)
    if err != nil {
      return -1, "", err
    }
		return cache_dirfd, cName, nil
  }
	// Open cipherdir (following symlinks)
	dirfd, err = syscall.Open(sessions[sessionID].root_cipher_dir, syscall.O_DIRECTORY|syscallcompat.O_PATH, 0)
	if err != nil {
		return -1, "", err
	}
	// If relPath is empty, cName is ".".
	if relPath == "" {
		return dirfd, ".", nil
	}
	// Walk the directory tree
	parts := strings.Split(relPath, "/")
	for i, name := range parts {
		iv, err := nametransform.ReadDirIVAt(dirfd)
		if err != nil {
			syscall.Close(dirfd)
			return -1, "", err
		}
		cName, err = sessions[sessionID].nameTransform.EncryptAndHashName(name, iv)
		if err != nil {
			syscall.Close(dirfd)
			return -1, "", err
		}
		// Last part? We are done.
		if i == len(parts)-1 {
      cache_dirfd, err := syscall.Dup(dirfd)
    	if err == nil {
    	    var dirRelPathCopy strings.Builder
            dirRelPathCopy.WriteString(dirRelPath)
    		sessions[sessionID].dirCache[dirRelPathCopy.String()] = Directory{cache_dirfd, iv}
    	}
			break
		}
		// Not the last part? Descend into next directory.
		dirfd2, err := syscallcompat.Openat(dirfd, cName, syscall.O_NOFOLLOW|syscall.O_DIRECTORY|syscallcompat.O_PATH, 0)
    syscall.Close(dirfd)
		if err != nil {
			return -1, "", err
		}
		dirfd = dirfd2
	}
	return dirfd, cName, nil
}

func mkdirWithIv(dirfd int, cName string, mode uint32) error {
	err := syscallcompat.Mkdirat(dirfd, cName, mode)
	if err != nil {
		return err
	}
	dirfd2, err := syscallcompat.Openat(dirfd, cName, syscall.O_DIRECTORY|syscall.O_NOFOLLOW|syscallcompat.O_PATH, 0)
	if err == nil {
		// Create gocryptfs.diriv
		err = nametransform.WriteDirIVAt(dirfd2)
		syscall.Close(dirfd2)
	}
	if err != nil {
		// Delete inconsistent directory (missing gocryptfs.diriv!)
		err2 := syscallcompat.Unlinkat(dirfd, cName, unix.AT_REMOVEDIR)
		if err2 != nil {
			return err2
		}
	}
	return err
}

func mangleOpenFlags(flags uint32) (newFlags int) {
	newFlags = int(flags)
	// Convert WRONLY to RDWR. We always need read access to do read-modify-write cycles.
	if (newFlags & syscall.O_ACCMODE) == syscall.O_WRONLY {
		newFlags = newFlags ^ os.O_WRONLY | os.O_RDWR
	}
	// We also cannot open the file in append mode, we need to seek back for RMW
	newFlags = newFlags &^ os.O_APPEND
	// O_DIRECT accesses must be aligned in both offset and length. Due to our
	// crypto header, alignment will be off, even if userspace makes aligned
	// accesses. Running xfstests generic/013 on ext4 used to trigger lots of
	// EINVAL errors due to missing alignment. Just fall back to buffered IO.
	newFlags = newFlags &^ syscallcompat.O_DIRECT
	// Create and Open are two separate FUSE operations, so O_CREAT should not
	// be part of the open flags.
	newFlags = newFlags &^ syscall.O_CREAT
	// We always want O_NOFOLLOW to be safe against symlink races
	newFlags |= syscall.O_NOFOLLOW
	return newFlags
}

func register_file_handle(sessionID int, file File) int {
  handleID := -1
  c := 0
  for handleID == -1 {
    _, ok := sessions[sessionID].file_handles[c]
    if !ok {
      handleID = c
    }
    c++
  }
  sessions[sessionID].file_handles[handleID] = file
  return handleID
}

func readFileID(fd *os.File) ([]byte, error) {
	// We read +1 byte to determine if the file has actual content
	// and not only the header. A header-only file will be considered empty.
	// This makes File ID poisoning more difficult.
	readLen := contentenc.HeaderLen + 1
	buf := make([]byte, readLen)
	_, err := fd.ReadAt(buf, 0)
	if err != nil {
		return nil, err
	}
	buf = buf[:contentenc.HeaderLen]
	h, err := contentenc.ParseHeader(buf)
	if err != nil {
		return nil, err
	}
	return h.ID, nil
}

func createHeader(fd *os.File) (fileID []byte, err error) {
  h := contentenc.RandomHeader()
	buf := h.Pack()
	// Prevent partially written (=corrupt) header by preallocating the space beforehand
	//NoPrealloc
  err = syscallcompat.EnospcPrealloc(int(fd.Fd()), 0, contentenc.HeaderLen)
  if err != nil {
    return nil, err
  }
	// Actually write header
	_, err = fd.WriteAt(buf, 0)
	if err != nil {
		return nil, err
	}
	return h.ID, err
}

func doRead(sessionID, handleID int, dst_buff []byte, offset uint64, length uint64) ([]byte, bool) {
  f, ok := sessions[sessionID].file_handles[handleID]
  if !ok {
    return nil, false
  }
  fd := f.fd
  var fileID []byte
  test_fileID, ok := sessions[sessionID].fileIDs[handleID]
  if ok {
    fileID = test_fileID
  } else {
    var err error
    fileID, err = readFileID(fd)
    if err != nil || fileID == nil {
      return nil, false
    }
    sessions[sessionID].fileIDs[handleID] = fileID
  }

	// Read the backing ciphertext in one go
	blocks := sessions[sessionID].contentEnc.ExplodePlainRange(offset, length)
	alignedOffset, alignedLength := blocks[0].JointCiphertextRange(blocks)
	skip := blocks[0].Skip

	ciphertext := sessions[sessionID].contentEnc.CReqPool.Get()
	ciphertext = ciphertext[:int(alignedLength)]
	n, err := fd.ReadAt(ciphertext, int64(alignedOffset))
	if err != nil && err != io.EOF {
                return nil, false
	}
	// The ReadAt came back empty. We can skip all the decryption and return early.
	if n == 0 {
		sessions[sessionID].contentEnc.CReqPool.Put(ciphertext)
		return dst_buff, true
	}
	// Truncate ciphertext buffer down to actually read bytes
	ciphertext = ciphertext[0:n]

	firstBlockNo := blocks[0].BlockNo

	// Decrypt it
	plaintext, err := sessions[sessionID].contentEnc.DecryptBlocks(ciphertext, firstBlockNo, fileID)
	sessions[sessionID].contentEnc.CReqPool.Put(ciphertext)
	if err != nil {
		return nil, false
	}

	// Crop down to the relevant part
	var out []byte
	lenHave := len(plaintext)
	lenWant := int(skip + length)
	if lenHave > lenWant {
		out = plaintext[skip:lenWant]
	} else if lenHave > int(skip) {
		out = plaintext[skip:lenHave]
	}
	// else: out stays empty, file was smaller than the requested offset

	out = append(dst_buff, out...)
	sessions[sessionID].contentEnc.PReqPool.Put(plaintext)
  return out, true
}

func doWrite(sessionID, handleID int, data []byte, offset uint64) (uint32, bool){
  fileWasEmpty := false
  f, ok := sessions[sessionID].file_handles[handleID]
  if !ok {
    return 0, false
  }
  fd := f.fd
  var err error
  var fileID []byte
  test_fileID, ok := sessions[sessionID].fileIDs[handleID]
  if ok {
    fileID = test_fileID
  } else {
    fileID, err = readFileID(fd)
    // Write a new file header if the file is empty
    if err == io.EOF {
      fileID, err = createHeader(fd)
      fileWasEmpty = true
    }
    if err != nil {
      return 0, false
    }
    sessions[sessionID].fileIDs[handleID] = fileID
  }
  // Handle payload data
  dataBuf := bytes.NewBuffer(data)
  blocks := sessions[sessionID].contentEnc.ExplodePlainRange(offset, uint64(len(data)))
  toEncrypt := make([][]byte, len(blocks))
  for i, b := range blocks {
    blockData := dataBuf.Next(int(b.Length))
    // Incomplete block -> Read-Modify-Write
    if b.IsPartial() {
      // Read
      oldData, success := doRead(sessionID, handleID, nil, b.BlockPlainOff(), sessions[sessionID].contentEnc.PlainBS())
      if !success {
        return 0, false
      }
      // Modify
      blockData = sessions[sessionID].contentEnc.MergeBlocks(oldData, blockData, int(b.Skip))
    }
    // Write into the to-encrypt list
    toEncrypt[i] = blockData
  }
  // Encrypt all blocks
  ciphertext := sessions[sessionID].contentEnc.EncryptBlocks(toEncrypt, blocks[0].BlockNo, fileID)
  // Preallocate so we cannot run out of space in the middle of the write.
  // This prevents partially written (=corrupt) blocks.
  cOff := int64(blocks[0].BlockCipherOff())

  //NoPrealloc
  err = syscallcompat.EnospcPrealloc(int(fd.Fd()), cOff, int64(len(ciphertext)))
  if err != nil {
    if fileWasEmpty {
      syscall.Ftruncate(int(fd.Fd()), 0)
      // Kill the file header again
      gcf_close_file(sessionID, handleID) //f.fileTableEntry.ID = nil
    }
    return 0, false
  }
  // Write
  _, err = fd.WriteAt(ciphertext, cOff)
  // Return memory to CReqPool
  sessions[sessionID].contentEnc.CReqPool.Put(ciphertext)
  if err != nil {
    return 0, false
  }
  return uint32(len(data)), true
}

// Zero-pad the file of size plainSize to the next block boundary. This is a no-op
// if the file is already block-aligned.
func zeroPad(sessionID, handleID int, plainSize uint64) bool {
	lastBlockLen := plainSize % sessions[sessionID].contentEnc.PlainBS()
	if lastBlockLen == 0 {
		// Already block-aligned
		return true
	}
	missing := sessions[sessionID].contentEnc.PlainBS() - lastBlockLen
	pad := make([]byte, missing)
	_, success := doWrite(sessionID, handleID, pad, plainSize)
	return success
}

// truncateGrowFile extends a file using seeking or ftruncate performing RMW on
// the first and last block as necessary. New blocks in the middle become
// file holes unless they have been fallocate()'d beforehand.
func truncateGrowFile(sessionID, handleID int, oldPlainSz uint64, newPlainSz uint64) bool {
	if newPlainSz <= oldPlainSz {
		return false
	}
	newEOFOffset := newPlainSz - 1
	if oldPlainSz > 0 {
		n1 := sessions[sessionID].contentEnc.PlainOffToBlockNo(oldPlainSz - 1)
		n2 := sessions[sessionID].contentEnc.PlainOffToBlockNo(newEOFOffset)
		// The file is grown within one block, no need to pad anything.
		// Write a single zero to the last byte and let doWrite figure out the RMW.
		if n1 == n2 {
			buf := make([]byte, 1)
			_, success := doWrite(sessionID, handleID, buf, newEOFOffset)
			return success
		}
	}
	// The truncate creates at least one new block.
	//
	// Make sure the old last block is padded to the block boundary. This call
	// is a no-op if it is already block-aligned.
  success := zeroPad(sessionID, handleID, oldPlainSz)
	if !success {
		return false
	}
	// The new size is block-aligned. In this case we can do everything ourselves
	// and avoid the call to doWrite.
	if newPlainSz%sessions[sessionID].contentEnc.PlainBS() == 0 {
		// The file was empty, so it did not have a header. Create one.
		if oldPlainSz == 0 {
			id, err := createHeader(sessions[sessionID].file_handles[handleID].fd)
			if err != nil {
				return false
			}
			sessions[sessionID].fileIDs[handleID] = id
		}
		cSz := int64(sessions[sessionID].contentEnc.PlainSizeToCipherSize(newPlainSz))
		return err_to_bool(syscall.Ftruncate(int(sessions[sessionID].file_handles[handleID].fd.Fd()), cSz))
	}
	// The new size is NOT aligned, so we need to write a partial block.
	// Write a single zero to the last byte and let doWrite figure it out.
	buf := make([]byte, 1)
	_, success = doWrite(sessionID, handleID, buf, newEOFOffset)
	return success
}

func truncate(sessionID, handleID int, newSize uint64) bool {
  fileFD := int(sessions[sessionID].file_handles[handleID].fd.Fd())
  /*// Common case first: Truncate to zero
	if newSize == 0 {
		err = syscall.Ftruncate(fileFD, 0)
		if err != nil {
			return false
		}
		// Truncate to zero kills the file header
		f.fileTableEntry.ID = nil
		return true
	}*/
  // We need the old file size to determine if we are growing or shrinking
  // the file
  oldSize, _, success := gcf_get_attrs(sessionID, sessions[sessionID].file_handles[handleID].path)
  if !success {
    return false
  }

  // File size stays the same - nothing to do
  if newSize == oldSize {
    return true
  }
  // File grows
  if newSize > oldSize {
    return truncateGrowFile(sessionID, handleID, oldSize, newSize)
  }

  // File shrinks
  blockNo := sessions[sessionID].contentEnc.PlainOffToBlockNo(newSize)
  cipherOff := sessions[sessionID].contentEnc.BlockNoToCipherOff(blockNo)
  plainOff := sessions[sessionID].contentEnc.BlockNoToPlainOff(blockNo)
  lastBlockLen := newSize - plainOff
  var data []byte
  if lastBlockLen > 0 {
    data, success = doRead(sessionID, handleID, nil, plainOff, lastBlockLen)
    if !success {
      return false
    }
  }
  // Truncate down to the last complete block
  err := syscall.Ftruncate(fileFD, int64(cipherOff))
  if err != nil {
    return false
  }
  // Append partial block
  if lastBlockLen > 0 {
    _, success := doWrite(sessionID, handleID, data, plainOff)
    return success
  }
  return true
}

func init_new_session(root_cipher_dir string, masterkey []byte) int {
  // Initialize EME for filename encryption.
  var emeCipher *eme.EMECipher
  var err error
  var emeBlockCipher cipher.Block
  emeKey := cryptocore.HkdfDerive(masterkey, cryptocore.HkdfInfoEMENames, cryptocore.KeyLen)
  emeBlockCipher, err = aes.NewCipher(emeKey)
  for i := range emeKey {
    emeKey[i] = 0
  }
  if err == nil {
    var new_session SessionVars
    emeCipher = eme.New(emeBlockCipher)
    new_session.nameTransform = nametransform.New(emeCipher, true, true)

    // Initialize contentEnc
    cryptoBackend := cryptocore.BackendGoGCM
    if stupidgcm.PreferOpenSSL() {
      cryptoBackend = cryptocore.BackendOpenSSL
    }
    forcedecode := false
    new_session.cryptoCore = cryptocore.New(masterkey, cryptoBackend, contentenc.DefaultIVBits, true, forcedecode)
    new_session.contentEnc = contentenc.New(new_session.cryptoCore, contentenc.DefaultBS, forcedecode)

    //copying root_cipher_dir
    var grcd strings.Builder
    grcd.WriteString(root_cipher_dir)
    new_session.root_cipher_dir = grcd.String()

    // New empty caches
    new_session.dirCache = make(map[string]Directory)
    new_session.file_handles = make(map[int]File)
    new_session.fileIDs = make(map[int][]byte)

    //find unused sessionID
    sessionID := -1
    c := 0
    for sessionID == -1 {
      _, ok := sessions[c]
      if !ok {
        sessionID = c
      }
      c++
    }
    if sessions == nil {
        sessions = make(map[int]SessionVars)
    }
    sessions[sessionID] = new_session;
    return sessionID
  }
  return -1
}

//export gcf_init
func gcf_init(root_cipher_dir string, password, givenScryptHash, returnedScryptHashBuff []byte) int {
  sessionID := -1
  cf, err := configfile.Load(filepath.Join(root_cipher_dir, configfile.ConfDefaultName))
  if err == nil {
    masterkey := cf.GetMasterkey(password, givenScryptHash, returnedScryptHashBuff)
    if masterkey != nil {
      sessionID = init_new_session(root_cipher_dir, masterkey)
      wipe(masterkey)
    }
  }
  return sessionID
}

//export gcf_close
func gcf_close(sessionID int){
  sessions[sessionID].cryptoCore.Wipe()
  for handleID, _ := range sessions[sessionID].file_handles {
    gcf_close_file(sessionID, handleID)
  }
  clear_dirCache(sessionID)
  delete(sessions, sessionID)
}

//export gcf_create_volume
func gcf_create_volume(root_cipher_dir string, password []byte, logN int, creator string) bool {
  err := configfile.Create(filepath.Join(root_cipher_dir, configfile.ConfDefaultName), password, false, logN, creator, false, false)
  if err == nil {
    dirfd, err := syscall.Open(root_cipher_dir, syscall.O_DIRECTORY|syscallcompat.O_PATH, 0)
    if err == nil {
      err = nametransform.WriteDirIVAt(dirfd)
      syscall.Close(dirfd)
      return err_to_bool(err)
    }
  }
  return false
}

//export gcf_change_password
func gcf_change_password(root_cipher_dir string, old_password, givenScryptHash, new_password, returnedScryptHashBuff []byte) bool {
  success := false
  cf, err := configfile.Load(filepath.Join(root_cipher_dir, configfile.ConfDefaultName))
  if err == nil {
    masterkey := cf.GetMasterkey(old_password, givenScryptHash, nil)
    if masterkey != nil {
      logN := cf.ScryptObject.LogN()
      scryptHash := cf.EncryptKey(masterkey, new_password, logN, len(returnedScryptHashBuff)>0)
      wipe(masterkey)
      for i := range scryptHash {
        returnedScryptHashBuff[i] = scryptHash[i]
        scryptHash[i] = 0
      }
      success = err_to_bool(cf.WriteFile())
    }
  }
  return success
}

//export gcf_list_dir
func gcf_list_dir(sessionID int, dirName string) (*C.char, *C.int, C.int) {
  parentDirFd, cDirName, err := openBackingDir(sessionID, dirName)
  if err != nil {
		return nil, nil, 0
	}
  defer syscall.Close(parentDirFd)
	// Read ciphertext directory
	var cipherEntries []syscallcompat.DirEntry
	fd, err := syscallcompat.Openat(parentDirFd, cDirName, syscall.O_RDONLY|syscall.O_DIRECTORY|syscall.O_NOFOLLOW, 0)
  if err != nil {
		return nil, nil, 0
	}
	defer syscall.Close(fd)
	cipherEntries, err = syscallcompat.Getdents(fd)
	if err != nil {
		return nil, nil, 0
	}
	// Get DirIV (stays nil if PlaintextNames is used)
	var cachedIV []byte
  // Read the DirIV from disk
  cachedIV, err = nametransform.ReadDirIVAt(fd)
  if err != nil {
    return nil, nil, 0
  }
	// Decrypted directory entries
  var plain strings.Builder
  var modes []uint32
	// Filter and decrypt filenames
	for i := range cipherEntries {
		cName := cipherEntries[i].Name
		if dirName == "" && cName == configfile.ConfDefaultName {
			// silently ignore "gocryptfs.conf" in the top level dir
			continue
		}
		if cName == nametransform.DirIVFilename {
			// silently ignore "gocryptfs.diriv" everywhere if dirIV is enabled
			continue
		}
		// Handle long file name
		isLong := nametransform.NameType(cName)
		if isLong == nametransform.LongNameContent {
			cNameLong, err := nametransform.ReadLongNameAt(fd, cName)
			if err != nil {
				continue
			}
			cName = cNameLong
		} else if isLong == nametransform.LongNameFilename {
			// ignore "gocryptfs.longname.*.name"
			continue
		}
		name, err := sessions[sessionID].nameTransform.DecryptName(cName, cachedIV)
		if err != nil {
			continue
		}
		// Override the ciphertext name with the plaintext name but reuse the rest
		// of the structure
		cipherEntries[i].Name = name
		plain.WriteString(cipherEntries[i].Name+"\x00")
    modes = append(modes, cipherEntries[i].Mode)
	}
  p := C.malloc(C.ulong(C.sizeof_int*len(modes)))
  for i := 0; i < len(modes); i++ {
    offset := C.sizeof_int*uintptr(i)
    *(*C.int)(unsafe.Pointer(uintptr(p)+offset)) = (C.int)(modes[i])
  }
  return C.CString(plain.String()), (*C.int)(p), (C.int)(len(modes))
}

//export gcf_mkdir
func gcf_mkdir(sessionID int, newPath string) bool {
	dirfd, cName, err := openBackingDir(sessionID, newPath)
	if err != nil {
		return false
	}
	defer syscall.Close(dirfd)
	// We need write and execute permissions to create gocryptfs.diriv.
	// Also, we need read permissions to open the directory (to avoid
	// race-conditions between getting and setting the mode).
	origMode := folder_mode
	mode := folder_mode | 0700

	// Handle long file name
	if nametransform.IsLongContent(cName) {
		// Create ".name"
		err = sessions[sessionID].nameTransform.WriteLongNameAt(dirfd, cName, newPath)
		if err != nil {
			return false
		}

		// Create directory
		err = mkdirWithIv(dirfd, cName, mode)
		if err != nil {
			nametransform.DeleteLongNameAt(dirfd, cName)
			return false
		}
	} else {
		err = mkdirWithIv(dirfd, cName, mode)
		if err != nil {
			return false
		}
	}
	// Set mode
	if origMode != mode {
		dirfd2, err := syscallcompat.Openat(dirfd, cName,
			syscall.O_RDONLY|syscall.O_DIRECTORY|syscall.O_NOFOLLOW, 0)
		if err != nil {
			return false
		}
		defer syscall.Close(dirfd2)

		var st syscall.Stat_t
		err = syscall.Fstat(dirfd2, &st)
		if err != nil {
			return false
		}

		// Preserve SGID bit if it was set due to inheritance.
		origMode = uint32(st.Mode&^0777) | origMode
		err = syscall.Fchmod(dirfd2, origMode)
		if err != nil {
			return false
		}
	}
	return true
}

//export gcf_rmdir
func gcf_rmdir(sessionID int, relPath string) bool {
  defer clear_dirCache(sessionID)
  parentDirFd, cName, err := openBackingDir(sessionID, relPath)
	if err != nil {
		return false
	}
	defer syscall.Close(parentDirFd)
	dirfd, err := syscallcompat.Openat(parentDirFd, cName, syscall.O_RDONLY|syscall.O_DIRECTORY|syscall.O_NOFOLLOW, 0)
	if err != nil {
		return false
	}
	defer syscall.Close(dirfd)
	// Check directory contents
	children, err := syscallcompat.Getdents(dirfd)
	if err == io.EOF {
		// The directory is empty
		err = unix.Unlinkat(parentDirFd, cName, unix.AT_REMOVEDIR)
		return err_to_bool(err)
	}
	if err != nil {
		return false
	}
	// If the directory is not empty besides gocryptfs.diriv, do not even
	// attempt the dance around gocryptfs.diriv.
	if len(children) > 1 {
		return false
	}
	// Move "gocryptfs.diriv" to the parent dir as "gocryptfs.diriv.rmdir.XYZ"
	tmpName := fmt.Sprintf("%s.rmdir.%d", nametransform.DirIVFilename, cryptocore.RandUint64())
	err = syscallcompat.Renameat(dirfd, nametransform.DirIVFilename, parentDirFd, tmpName)
	if err != nil {
		return false
	}
	// Actual Rmdir
	err = syscallcompat.Unlinkat(parentDirFd, cName, unix.AT_REMOVEDIR)
	if err != nil {
		// This can happen if another file in the directory was created in the
		// meantime, undo the rename
		err2 := syscallcompat.Renameat(parentDirFd, tmpName, dirfd, nametransform.DirIVFilename)
		return err_to_bool(err2)
	}
	// Delete "gocryptfs.diriv.rmdir.XYZ"
	err = syscallcompat.Unlinkat(parentDirFd, tmpName, 0)
	// Delete .name file
	if nametransform.IsLongContent(cName) {
		nametransform.DeleteLongNameAt(parentDirFd, cName)
	}
	return true
}

//export gcf_open_read_mode
func gcf_open_read_mode(sessionID int, path string) int {
  newFlags := mangleOpenFlags(0)
  dirfd, cName, err := openBackingDir(sessionID, path)
	if err != nil {
		return -1
	}
	defer syscall.Close(dirfd)
	fd, err := syscallcompat.Openat(dirfd, cName, newFlags, 0)
	if err != nil {
		return -1
	}
	return register_file_handle(sessionID, File{os.NewFile(uintptr(fd), cName), path})
}

//export gcf_open_write_mode
func gcf_open_write_mode(sessionID int, path string) int {
	newFlags := mangleOpenFlags(syscall.O_RDWR)
	dirfd, cName, err := openBackingDir(sessionID, path)
	if err != nil {
		return -1
	}
	defer syscall.Close(dirfd)
	fd := -1
	// Handle long file name
	if nametransform.IsLongContent(cName) {
		// Create ".name"
		err = sessions[sessionID].nameTransform.WriteLongNameAt(dirfd, cName, path)
		if err != nil {
			return -1
		}
		// Create content
			fd, err = syscallcompat.Openat(dirfd, cName, newFlags|syscall.O_CREAT, file_mode)
		if err != nil {
			nametransform.DeleteLongNameAt(dirfd, cName)
		}
	} else {
		// Create content, normal (short) file name
		fd, err = syscallcompat.Openat(dirfd, cName, newFlags|syscall.O_CREAT, file_mode)
	}
	if err != nil {
		// xfstests generic/488 triggers this
		if err == syscall.EMFILE {
			var lim syscall.Rlimit
			syscall.Getrlimit(syscall.RLIMIT_NOFILE, &lim)
		}
		return -1
	}
	return register_file_handle(sessionID, File{os.NewFile(uintptr(fd), cName), path})
}

//export gcf_truncate
func gcf_truncate(sessionID int, path string, offset uint64) bool {
  handleID := gcf_open_write_mode(sessionID, path)
  if handleID != -1 {
    success := truncate(sessionID, handleID, offset)
    gcf_close_file(sessionID, handleID)
    return success
  }
  return false
}

//export gcf_close_file
func gcf_close_file(sessionID, handleID int){
  f, ok := sessions[sessionID].file_handles[handleID]
  if ok {
    f.fd.Close()
    delete(sessions[sessionID].file_handles, handleID)
    _, ok := sessions[sessionID].fileIDs[handleID]
    if ok {
      delete(sessions[sessionID].fileIDs, handleID)
    }
  }
}

//export gcf_read_file
func gcf_read_file(sessionID, handleID int, offset uint64, dst_buff []byte) uint32 {
  length := uint64(len(dst_buff))
  if length > contentenc.MAX_KERNEL_WRITE {
    return 0;
  }

  out, _ := doRead(sessionID, handleID, dst_buff[:0], offset, length)

	return uint32(len(out))
}

//export gcf_write_file
func gcf_write_file(sessionID, handleID int, offset uint64, data []byte) uint32 {
  length := uint64(len(data))
  if length > contentenc.MAX_KERNEL_WRITE {
    return 0;
  }

  written, _ := doWrite(sessionID, handleID, data, offset)

  return written
}

//export gcf_get_attrs
func gcf_get_attrs(sessionID int, relPath string) (uint64, int64, bool) {
	dirfd, cName, err := openBackingDir(sessionID, relPath)
	if err != nil {
		return 0, 0, false
	}
	var st unix.Stat_t
	err = syscallcompat.Fstatat(dirfd, cName, &st, unix.AT_SYMLINK_NOFOLLOW)
	syscall.Close(dirfd)
	if err != nil {
		return 0, 0, false
	}
  return sessions[sessionID].contentEnc.CipherSizeToPlainSize(uint64(st.Size)), st.Mtim.Sec, true
}

//export gcf_rename
func gcf_rename(sessionID int, oldPath string, newPath string) bool {
	defer clear_dirCache(sessionID)
	oldDirfd, oldCName, err := openBackingDir(sessionID, oldPath)
	if err != nil {
		return false
	}
	defer syscall.Close(oldDirfd)
	newDirfd, newCName, err := openBackingDir(sessionID, newPath)
	if err != nil {
		return false
	}
	defer syscall.Close(newDirfd)
	// Long destination file name: create .name file
	nameFileAlreadyThere := false
	if nametransform.IsLongContent(newCName) {
		err = sessions[sessionID].nameTransform.WriteLongNameAt(newDirfd, newCName, newPath)
		// Failure to write the .name file is expected when the target path already
		// exists. Since hashes are pretty unique, there is no need to modify the
		// .name file in this case, and we ignore the error.
		if err == syscall.EEXIST {
			nameFileAlreadyThere = true
		} else if err != nil {
			return false
		}
	}
	// Actual rename
	err = syscallcompat.Renameat(oldDirfd, oldCName, newDirfd, newCName)
	if err == syscall.ENOTEMPTY || err == syscall.EEXIST {
		// If an empty directory is overwritten we will always get an error as
		// the "empty" directory will still contain gocryptfs.diriv.
		// Interestingly, ext4 returns ENOTEMPTY while xfs returns EEXIST.
		// We handle that by trying to fs.Rmdir() the target directory and trying
		// again.
		if gcf_rmdir(sessionID, newPath) {
			err = syscallcompat.Renameat(oldDirfd, oldCName, newDirfd, newCName)
		}
	}
	if err != nil {
		if nametransform.IsLongContent(newCName) && nameFileAlreadyThere == false {
			// Roll back .name creation unless the .name file was already there
			nametransform.DeleteLongNameAt(newDirfd, newCName)
		}
		return false
	}
	if nametransform.IsLongContent(oldCName) {
		nametransform.DeleteLongNameAt(oldDirfd, oldCName)
	}
	return true
}

//export gcf_remove_file
func gcf_remove_file(sessionID int, path string) bool {
	dirfd, cName, err := openBackingDir(sessionID, path)
	if err != nil {
		return false
	}
	defer syscall.Close(dirfd)
	// Delete content
	err = syscallcompat.Unlinkat(dirfd, cName, 0)
	if err != nil {
		return false
	}
	// Delete ".name" file
	if nametransform.IsLongContent(cName) {
		err = nametransform.DeleteLongNameAt(dirfd, cName)
	}
	return err_to_bool(err)
}

func main(){}
