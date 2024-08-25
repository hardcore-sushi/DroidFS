# Update the modified CameraX files to a new upstream version:

Create the `new` folder if needed:
```
mkdir -p new
```

Put the new CameraX files from upstream (`androidx.camera.video.Recorder`, `androidx.camera.video.Recording`, `androidx.camera.video.PendingRecording` and `androidx.camera.video.internal.encoder.EncoderImpl`) in the `new` folder.

Perform the 3 way merge:
```
./merge.sh
```

If new files are created in the current directory, they contains conflicts. Resolve them then move them to the right location.

Finally, update the base:
```
./update.sh
```
