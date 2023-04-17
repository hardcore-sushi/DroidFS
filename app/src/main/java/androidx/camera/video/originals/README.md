# Update the modified CameraX files to a new upstream version:

Create the `new` folder if needed:
```
mkdir -p new
```

Put new CameraX files from upstream in the `new` folder.

Perform the 3 way merge:
```
./merge.sh
```

If new files are created in the current directory, they contains conflicts. Resolve them then move them to the right location.

Finally, update the base:
```
./update.sh
```
