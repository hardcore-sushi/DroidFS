// Copyright 2012 Aaron Jacobs. All Rights Reserved.
// Author: aaronjjacobs@gmail.com (Aaron Jacobs)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package siv

import (
	"bytes"
	"crypto/aes"
	"fmt"

	"../cmac"
	"../common"
)

var s2vZero []byte

func init() {
	s2vZero = bytes.Repeat([]byte{0x00}, aes.BlockSize)
}

// The output size of the s2v function.
const s2vSize = cmac.Size

// Run the S2V "string to vector" function of RFC 5297 using the input key and
// string vector, which must be non-empty. (RFC 5297 defines S2V to handle the
// empty vector case, but it is never used that way by higher-level functions.)
//
// If provided, the supplied scatch space will be used to avoid an allocation.
// It should be (but is not required to be) as large as the last element of
// strings.
//
// The result is guaranteed to be of length s2vSize.
func s2v(key []byte, strings [][]byte, scratch []byte) []byte {
	numStrings := len(strings)
	if numStrings == 0 {
		panic("strings vector must be non-empty.")
	}

	// Create a CMAC hash.
	h, err := cmac.New(key)
	if err != nil {
		panic(fmt.Sprintf("cmac.New: %v", err))
	}

	// Initialize.
	if _, err := h.Write(s2vZero); err != nil {
		panic(fmt.Sprintf("h.Write: %v", err))
	}

	d := h.Sum([]byte{})
	h.Reset()

	// Handle all strings but the last.
	for i := 0; i < numStrings-1; i++ {
		if _, err := h.Write(strings[i]); err != nil {
			panic(fmt.Sprintf("h.Write: %v", err))
		}

		common.Xor(d, dbl(d), h.Sum([]byte{}))
		h.Reset()
	}

	// Handle the last string.
	lastString := strings[numStrings-1]
	var t []byte
	if len(lastString) >= aes.BlockSize {
		// Make an output buffer the length of lastString.
		if cap(scratch) >= len(lastString) {
			t = scratch[:len(lastString)]
		} else {
			t = make([]byte, len(lastString))
		}

		// XOR d on the end of lastString.
		xorend(t, lastString, d)
	} else {
		t = make([]byte, aes.BlockSize)
		common.Xor(t, dbl(d), common.PadBlock(lastString))
	}

	if _, err := h.Write(t); err != nil {
		panic(fmt.Sprintf("h.Write: %v", err))
	}

	return h.Sum([]byte{})
}
