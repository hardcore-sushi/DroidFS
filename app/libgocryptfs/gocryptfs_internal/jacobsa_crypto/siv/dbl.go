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

	"../common"
)

var dblRb []byte

func init() {
	dblRb = append(bytes.Repeat([]byte{0x00}, 15), 0x87)
}

// Given a 128-bit binary string, shift the string left by one bit and XOR the
// result with 0x00...87 if the bit shifted off was one. This is the dbl
// function of RFC 5297.
func dbl(b []byte) []byte {
	if len(b) != aes.BlockSize {
		panic("dbl requires a 16-byte buffer.")
	}

	shiftedOne := common.Msb(b) == 1
	b = common.ShiftLeft(b)
	if shiftedOne {
		tmp := make([]byte, aes.BlockSize)
		common.Xor(tmp, b, dblRb)
		b = tmp
	}

	return b
}
