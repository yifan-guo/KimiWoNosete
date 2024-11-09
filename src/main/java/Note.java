public class Note {
    private String note;
    private double magnitude;
    private int index;
    
    public Note(String note, double magnitude, int index) {
        this.note = note;
        this.magnitude = magnitude;
        this.index = index;
    }
    
    public String getNote() {
        return note;
    }
    
    public double getMagnitude() {
        return magnitude;
    }
    
    public int getIndex() {
        return index;
    }
}