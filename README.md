# KimiWoNosete
A program to analyze .wav files of piano recordings and produce music scores

Requires Lilypond, a music engraving program to render the .ly file produced by this program.
Download can be found here: http://lilypond.org/

#Instructions
1. Pull repo
2. Open up the command line and navigate to this directory
3. compile "javac KimiWoNosete.java"
4. run "java KimiWoNosete [SAMPLE_SIZE] [STARTING_POINT]"  
    *[SAMPLE_SIZE] is an integer that represents the number of data points analyzed per sample (each sample computes the prominent frequencies)
    *[STARTING_POINT] is an integer that represents where in the sound file the program should start analyzing (i.e. computing samples)
    *a default option is "java KimiWoNosete 3000 0" (i.e. analyze this song from the beginning with 3000 data points per sample)
    *Note: the smaller the SAMPLE_SIZE the more precise the sheet score, since it is less likely for previously played notes to carry over into the next window of time, however runtime may be extended
5. Wait for the song to finish playing, as the program is writing the notes into a file called "test.txt"
6. Run "java LilypadFileGenerator" from the command line, as it is parsing the "test.txt" file into a file that can be read by Lilypond called "output.ly"
6. Open "output.ly", which launches Lilypond automatically, and select "Compile" (or press Cmd + R) from the Lilypond menu bar 
7. Lilypond will open your PDF viewer and display the sheet music
