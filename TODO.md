# TODO

Here's a list of features that it would be nice to have in DroidFS. As this is a FLOSS project, there are no special requirements on *when* or even *if* these features will be implemented, but contributions are greatly appreciated.

## Security
- [hardened_malloc](https://github.com/GrapheneOS/hardened_malloc) compatibility ([#181](https://github.com/hardcore-sushi/DroidFS/issues/181))
- Internal keyboard for passwords

## UX
- File associations editor
- Optional discovery before file operations
- Modifiable CryFS scrypt parameters
- Alert dialog showing details of file operations
- Internal file browser to select volumes

## Encryption software support
- [Shufflecake](https://shufflecake.net): plausible deniability for multiple hidden filesystems on Linux (would be absolutely awesome to have but quite difficult)
- [fscrypt](https://www.kernel.org/doc/html/latest/filesystems/fscrypt.html): filesystem encryption at the kernel level

## Health
- F-Droid ABI split
- OpenSSL & FFmpeg as git submodules (useful for F-Droid)
- Remove all android:configChanges from AndroidManifest.xml
- More efficient thumbnails cache
- Guide for translators
- Usage & code documentation
- Automated tests

## And:
- All the [feature requests on the GitHub repo](https://github.com/hardcore-sushi/DroidFS/issues?q=is%3Aissue+is%3Aopen+label%3Aenhancement)
- All the [feature requests on the Gitea repo](https://forge.chapril.org/hardcoresushi/DroidFS/issues?q=&state=open&labels=748)
