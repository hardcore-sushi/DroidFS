#!/bin/sh

set -e

for i in "PendingRecording" "Recording" "Recorder"; do
	diff3 -m ../Suckless$i.java base/$i.java new/$i.java > Suckless$i.java && mv Suckless$i.java ..
done
diff3 -m ../internal/encoder/SucklessEncoderImpl.java base/EncoderImpl.java new/EncoderImpl.java > SucklessEncoderImpl.java && mv SucklessEncoderImpl.java ../internal/encoder/SucklessEncoderImpl.java
