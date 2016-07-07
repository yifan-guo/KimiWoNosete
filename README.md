# KimiWoNosete
A program to analyze .wav files of piano recordings and produce music scores

Requires Lilypond, a music engraving program to render the .ly file produced by this program.
Download can be found here: http://lilypond.org/

#Instructions
1. Pull repo<br/>
2. Open up the command line and navigate to this directory<br/>
3. Compile "javac KimiWoNosete.java"<br/>
4. Type "java KimiWoNosete [SAMPLE_SIZE] [STARTING_POINT]"  and hit ENTER <br/>
    *[SAMPLE_SIZE] is an integer that represents the number of data points analyzed per sample (a set of points that represents a window of time in the song)<br/>
    *[STARTING_POINT] is an integer that represents where in the sound file the program should start analyzing <br/>
    *a default option is "java KimiWoNosete 3000 0" (i.e. analyze this song from the beginning with 3000 data points per sample)<br/>
    *Note: the smaller the SAMPLE_SIZE the more precise the sheet score, since it is less likely for previously played notes to carry over into the next window of time, however runtime may be extended<br/>
5. Type the name of the audio file to be analyzed (default: type "KimiWoNosete_1band.wav")<br/>
6. Wait for the song to finish playing, as the program is writing the notes into a file called "test.txt"<br/>
7. Compile "javac LilypadFileGenerator.java"<br/>
7. Run "java LilypadFileGenerator" from the command line, as it is parsing the "test.txt" file into a file that can be read by Lilypond called "output.ly"<br/>
8. Open "output.ly", which launches Lilypond automatically, and select "Compile" (or press Cmd + R) from the Lilypond menu bar <br/>
9. Lilypond will open your PDF viewer and display the sheet music<br/>
