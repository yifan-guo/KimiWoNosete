import java.io.*;                //for FileReader and BufferedReader
import java.util.*;     //for deque

public class LilypadFileGenerator {
    
    public static void main (String[] args) throws java.io.IOException{
        //prepare to read from file
        FileReader reader = new FileReader("test.ly");
        BufferedReader buffer = new BufferedReader(reader);
        final int MARK_LIMIT = 1000;
        String myLine;
        
        //setup output file to write to
        FileWriter writer = new FileWriter("output.ly");
        writer.write("\\absolute {\n");
        
        //variables to record magnitude and key on piano for every line & compare max magnitudes across adjacent samples
        Deque<Double> deque = new LinkedList<Double>();
        
        //get data from each line in test.ly
        while ( (myLine = buffer.readLine()) != null) {
            if (myLine.contains("START_OF_SAMPLE")) {
                String[] summary = myLine.split(" ");
                double max_sample = Double.parseDouble(summary[1]);//get max magnitude of that sample

                    if (deque.size() == 0) {
                        deque.addLast(max_sample);
                    }
                    else if (deque.size() == 1 && max_sample > deque.getLast()) {//mark
                        deque.addLast(max_sample);
                        buffer.mark(MARK_LIMIT);
                    }
                    else if (deque.size() == 1 && max_sample <= deque.getLast()) {
                        while (!deque.isEmpty()) {
                            deque.removeFirst();
                        }
                        deque.addLast(max_sample);
                    }
                    else if (deque.size() == 2 && max_sample < deque.getLast()) {   //peak
                        //retain the magnitude of the peak sample before removing it from the deque
                        double max_magnitude = deque.getLast();
                        
                        while (!deque.isEmpty()) {
                            deque.removeFirst();
                        }
                        deque.addLast(max_sample);
                        buffer.reset();
                        
                        //to count the number of loudest notes in the sample
                        ArrayList<Note> notes = new ArrayList<Note>();
                        
                        //write notes into music score  
                        while ((myLine = buffer.readLine()) != null && !(myLine = buffer.readLine()).contains("START_OF_SAMPLE")) {
                            //parse line for note, magnitude, and index
                            String[] note = myLine.split(":");
                            String[] data = note[1].split(" ");      //data[0] is magnitude, data[1] is index
                            double magnitude = Double.parseDouble(data[0]);
                            int index = Integer.parseInt(data[1]);
                            
                            if (magnitude >= max_magnitude - 35) {
                                notes.add(new Note(note[0], magnitude, index));
                            }
                        }
                        if (notes.size() > 1) {//chords
                            writeChord(notes, writer);
                        }
                        else if (notes.size() == 1) {//one note
                            writeNote(notes, writer);
                        }
                        buffer.mark(MARK_LIMIT);
                    }
                    else {//deque.size() == 2 && max_sample >= deque.getLast()
                        deque.addLast(max_sample);
                        deque.removeFirst();
                        buffer.mark(MARK_LIMIT);
                    }
                
            }
        }
        writer.write("\n}\n");
        writer.write("\\version \"2.14.0\"");
        writer.close();
    }
    
    public static void writeNote(ArrayList<Note> notes, FileWriter writer) throws java.io.IOException {
        for (Note s : notes) {
            int index = s.getIndex();
            if (index < 3 ){
                writer.write(s.getNote() + ",,, ");
            }
            else if (index < 15) {
                writer.write(s.getNote() + ",, ");
            }
            else if (index < 27) {
                writer.write(s.getNote() + ", ");
            }
            else if (index < 39) {
                writer.write(s.getNote() + " ");
            }
            else if (index < 51) {
                writer.write(s.getNote() + "' ");
            }
            else if (index < 63) {
                writer.write(s.getNote() + "'' ");
            }
            else if (index < 75) {
                writer.write(s.getNote() + "''' ");
            }
            else if (index < 87) {
                writer.write(s.getNote() + "'''' ");
            }
            else {//87
                writer.write(s.getNote() + "''''' ");
            }
        }
    }
           
    public static void writeChord(ArrayList<Note> notes, FileWriter writer) throws java.io.IOException{
        writer.write("<");
        for (Note s : notes) {
            int index = s.getIndex();
            if (index < 3 ){
                writer.write(s.getNote() + ",,, ");
            }
            else if (index < 15) {
                writer.write(s.getNote() + ",, ");
            }
            else if (index < 27) {
                writer.write(s.getNote() + ", ");
            }
            else if (index < 39) {
                writer.write(s.getNote() + " ");
            }
            else if (index < 51) {
                writer.write(s.getNote() + "' ");
            }
            else if (index < 63) {
                writer.write(s.getNote() + "'' ");
            }
            else if (index < 75) {
                writer.write(s.getNote() + "''' ");
            }
            else if (index < 87) {
                writer.write(s.getNote() + "'''' ");
            }
            else {//87
                writer.write(s.getNote() + "''''' ");
            }
        }
        writer.write("> ");
    }
}
    
    