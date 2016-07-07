import java.io.*;

public class KimiWoNosete
{
    //post:: returns the note that corresponds to the index arg
    public static String h1(int index, String[] notes) {
         return notes[index % 12];
    }
    //post:: returns the index (in the frequency array) with the smallest episilon value
    public static int computeEpsilon(double[] frequency, double fundfreq) {
        double epsilon = Double.MAX_VALUE;
        double difference = 0;
        int index = 0;
        for (int i = 0; i < frequency.length; i++) {
            difference = Math.abs(fundfreq - frequency[i]);
            if (difference < epsilon) {
                epsilon = difference;
                index = i;
            }
        }
        return index;
    }
    //post::prints out notes in the input sound file
    public static void main(String[] args) throws java.io.IOException
    { 
        int SAMPLES_PER_SECOND = 44100;
        int NUMBER_OF_KEYS = 12;
        String[] notes = new String[NUMBER_OF_KEYS];
        notes[0] = "a";
        notes[1] = "ais";
        notes[2] = "b";
        notes[3] = "c";
        notes[4] = "cis";
        notes[5] = "d";
        notes[6] = "dis";
        notes[7] = "e";
        notes[8] = "f";
        notes[9] = "fis";
        notes[10] = "g";
        notes[11] = "gis";
        
        double[] frequency = new double[88];
        for (int i = -48; i <=39; i++ ) {
            //initialize frequency array
            frequency[i+48] = 440 * Math.pow(2, (double)i/12);
            //System.out.printf("%.2f \n", frequency[i + 48]);
        }
        
        int N = Integer.parseInt(args[0]);               //size of the sample 
        int Start = Integer.parseInt(args[1]);           //index of the .wav file double array where the sample array starts
        String file = StdIn.readString();                //name of file
        double[] sound = StdAudio.read(file);            //read input .wav file into a double array
        
        FileWriter writer = new FileWriter("test.ly");
        
        //-----------Here we go--------------
        for (int probe = Start; probe + N < sound.length; probe = probe + N) {
        //-----------------Drawing the sampled audio data-----------------------
        //double soundmax = sound[0];
        //double soundmin = sound[0];
        double[] sample = new double[N];
        for( int i = probe; i < probe + N; i++)
        {
            sample[i - probe] = sound[i];//2
            /*if (sound[i] > soundmax)                          //find max and min values in the sound array
                soundmax = sound[i];
            if (sound[i] < soundmin)
                soundmin = sound[i];*/
        }
        StdAudio.play(sample);//3
        //StdDraw.setXscale(probe, probe + N);
        //StdDraw.setYscale(soundmin, soundmax);
        int FREQUENCY_RANGE = 32768;
        Complex[] NFFT = new Complex[FREQUENCY_RANGE]; 
        for (int i = 0; i < NFFT.length; i++)
        {
            NFFT[i] = new Complex(0, 0);           //zero-padded NFFT
        }

        int count = 0;
        for (int i = probe; i < probe + N; i++)
        {
            //Draw the points in a sample of the sound array
            //StdDraw.point(i, sound[i]);                 
            //initializing NFFT to sound values
            Complex temp = new Complex(sound[i], 0);    
            NFFT[count] = NFFT[count].plus(temp);
            count++;

        }
        //StdDraw.clear();
        //-----------------Drawing the FFT-----------------------
        int MAX_FREQUENCY = 7903;//2230
        Complex[] y = FFT.fft(NFFT);                     //take FFT of NFFT array
        double[] magnitude = new double[y.length/4];     //only keep half, was 2
        double max = Double.MIN_VALUE;
        //double min = Double.MAX_VALUE;
        double index = 0;              //index at which max occurs (fundamental frequency)
        for( int i = 0; i < magnitude.length; i++) {
            magnitude[i] = Math.sqrt(Math.pow(y[i].re(), 2) + Math.pow(y[i].im(), 2)); 
            //determine the index (freq) of the peak
            if (magnitude[i] > max) {
                max = magnitude[i];
                index = i;
            }
//            if (magnitude[i] < min)
//                 min = magnitude[i];
        }
        //StdDraw.setXscale(0, MAX_FREQUENCY);
        //StdDraw.setYscale(min, 350);
        //StdDraw.setPenColor(0, 200, 0);
//        for( int i = 0; i < magnitude.length - 1; i++)
//        {
//            StdDraw.line(i, magnitude[i], i + 1, magnitude[i+1]); 
//        }
        writer.write("START_OF_SAMPLE " + String.format("%.2f", max) + "\n");
        for (int i = 1; i < magnitude.length - 1; i++) {
            //print out notes within threshold 
            if (magnitude[i] > magnitude[i-1] && magnitude[i] > magnitude[i+1] && magnitude[i] > 0.40 * max) {
            int potential_note = computeEpsilon(frequency, ((double)i/NFFT.length) * SAMPLES_PER_SECOND);
            //System.out.printf(h1(potential_note, notes) + " Magnitude: %.2f" + "     Index: %s\n", magnitude[i], potential_note);
            writer.write(h1(potential_note, notes) + ":" + String.format("%.2f", magnitude[i]) + " " + potential_note + "\n");
            }
        }
        //System.out.println();
        //StdDraw.clear();
        
//        int index_of_note = computeEpsilon(frequency, (index/NFFT.length) * SAMPLES_PER_SECOND);     //frequency with the largest magnitude
//        StdOut.print((index/NFFT.length) * SAMPLES_PER_SECOND);
//        StdOut.print(h1(index_of_note, notes) + " ");
        }
    }
}

