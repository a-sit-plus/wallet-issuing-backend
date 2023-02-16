# Compile libwebp in a CentOS7 Docker

Environment:
- CentOS 7 (close to RHEL7)
- Java 11
- libwebp 1.3.0


Install docker:
```
sudo apt install docker.io docker
```

Change into directory:
```
cd docker-builder-libwebp
```

Build image:
```
docker build -t builder-webp .
```

Run & terminal into container; execute compile script:
```
docker run -it builder-webp /bin/bash
> ./compile.sh
> Ctrl+D
```

Extract relevant shared objects:
```
mkdir native
export CONTAINER=$(docker ps -a | awk '{print $NF}' | head -n2 | tail -n1)
docker cp -L $CONTAINER:/usr/local/lib/libwebp.so.7 libwebp.so.7
docker cp -L $CONTAINER:/usr/local/lib/libsharpyuv.so.0 libsharpyuv.so.0
docker cp $CONTAINER:/libwebp-1.3.0/swig/libwebp_jni.so libwebp_jni.so
docker rm $CONTAINER 
```
