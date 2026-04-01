cd ~

wget https://oracle.com

tar -xzvf jdk-21_linux-x64_bin.tar.gz

JDK_DIR=$(ls -d jdk-21*)

echo "export JAVA_HOME=\"\$HOME/$JDK_DIR\"" >> ~/.bashrc
echo "export PATH=\"\$JAVA_HOME/bin:\$PATH\"" >> ~/.bashrc

source ~/.bashrc
