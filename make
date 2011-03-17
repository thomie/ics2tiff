# Usage: ./make

# sudo apt-get imagej
# sudo apt-get source imagej
javac -classpath ../../forks/imagej-1.44c/ Ics_Opener_To_Tiff.java
jar cvfM Ics_Opener_To_Tiff.jar *.class *.java *.config make LICENSE README
ln -sf `pwd`/Ics_Opener_To_Tiff.jar $HOME/.imagej/plugins/Ics_Opener_To_Tiff.jar
