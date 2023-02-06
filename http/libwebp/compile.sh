cd /

yum -y install java-11-openjdk.x86_64 java-11-openjdk-devel.x86_64
yum -y install libpng-devel.x86_64
yum -y install gcc make automake
yum -y install wget

wget https://storage.googleapis.com/downloads.webmproject.org/releases/webp/libwebp-1.3.0.tar.gz

tar xvzf libwebp-1.3.0.tar.gz
cd libwebp-1.3.0
./configure
make
make install

cd swig
gcc -shared -fPIC -fno-strict-aliasing -O2 -I/usr/lib/jvm/java-11-openjdk/include -I/usr/lib/jvm/java-11-openjdk/include/linux libwebp_java_wrap.c -lwebp -o libwebp_jni.so
