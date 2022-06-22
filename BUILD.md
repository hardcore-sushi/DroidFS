```
git submodule update --depth=1 --init
```
```
for i in concepts meta std range; do
	ln -s /usr/include/$i $ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include;
done
```
