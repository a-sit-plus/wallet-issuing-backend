cd /

if command -v yum &> /dev/null
then
    yum -y install java-11-openjdk.x86_64 java-11-openjdk-devel.x86_64 libpng-devel.x86_64 gcc make automake wget
else
    apt -q update
    apt -yq install gcc wget make automake openjdk-11-jdk-headless openjdk-11-source wget
fi



wget https://storage.googleapis.com/downloads.webmproject.org/releases/webp/libwebp-1.3.0.tar.gz

tar xvzf libwebp-1.3.0.tar.gz
cd libwebp-1.3.0
./configure
make
make install

cd swig
gcc -shared -fPIC -fno-strict-aliasing -O2 -I/usr/lib/jvm/java-11-openjdk/include -I/usr/lib/jvm/java-11-openjdk/include/linux libwebp_java_wrap.c -lwebp -o libwebp_jni.so
