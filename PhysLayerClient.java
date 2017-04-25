import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.*;
import java.lang.String;
import java.lang.*;
import java.text.*;


public class PhysLayerClient {

    //holds value of high or low input from server
    private static double baseline = 0;

    public static void main(String[] args) {

        try (Socket socket = new Socket("codebank.xyz", 38002)) {
           
            System.out.println("\n\nConnected to server.\n");

            //Establishes baseline from server
            double establishedBaseline = getBaseline(socket);
            DecimalFormat decimal = new DecimalFormat("#.##");
            System.out.println("Baseline established from preamble: " + decimal.format(establishedBaseline));

            //Takes in next 320 bits form initial message before encoding
            LinkedList<Integer> fiveSig = signalMessageBits(socket);

            //Instantiates the 5b/4b conversion convTable for the first time and begins
            //encoding process
            LinkedList<Integer> conv5B = encode4B5B(fiveSig);

            //Uses given conversion convTable to decode the message 
            LinkedList<Integer> finalDecodedMessage = checkTable(conv5B);
            LinkedList<Integer> mergeBits = combineBits(finalDecodedMessage);
            talkToServer(socket, mergeBits);
            
            //Checks message 
            if (checkResponse(socket))
                System.out.println("Response Good!");
            else
                System.out.println("Response Bad.");


            socket.close();
            System.out.println("Disconnected from server.");

        } catch (Exception e) { e.printStackTrace(); }
    }

    // Absorbs the first 64 bits from the server and averages them to find the baseline
    public static double getBaseline(Socket socket) {

        try {
            InputStream is = socket.getInputStream();

            double preamble = 0;
            for(int i = 0; i < 64; ++i) {
                preamble += is.read();
            }

            baseline = preamble / 64;
            
           return baseline;

        } catch (Exception e) { e.printStackTrace(); }
        return 0.0;
    }

    // Receives the remaining 320 bits from the server and converts them based on 
    //high or low fiveSig
    public static LinkedList<Integer> signalMessageBits(Socket socket) {
        try {
            InputStream is = socket.getInputStream();
            LinkedList<Integer> fiveSig = new LinkedList<>();
            for(int i = 0; i < 320; ++i) {
                int input = is.read();
                fiveSig.add(highOrLow(input));
            }
            return fiveSig;
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    // Will reference every bitsToConvertlist of 5 bits from the NRZI finalDecodedMessage
    // message to determine what the actual data values are
    public static LinkedList<Integer> checkTable(LinkedList<Integer> fiveSig) {
        ConversionTable convTable = new ConversionTable();
        LinkedList<Integer> decodedMessage = new LinkedList<>();
        for(int i = 0; i < fiveSig.size() - 4; i++) {
            LinkedList<Integer> bitsToConvert = new LinkedList<>();
            bitsToConvert.add(fiveSig.get(i));
            bitsToConvert.add(fiveSig.get(i+1));
            bitsToConvert.add(fiveSig.get(i+2));
            bitsToConvert.add(fiveSig.get(i+3));
            bitsToConvert.add(fiveSig.get(i+4));
            i+=4;
            decodedMessage.add(convTable.fiveBitConvKey(bitsToConvert));
        }
        return decodedMessage;
    }

    // Determines if the input is high or low, returns 0 or 1
    public static int highOrLow(int check) {
        if (check > baseline)
            return 1;
        else
            return 0;
    }

    // Decodes 4b/5b bit message
    public static LinkedList<Integer> encode4B5B(LinkedList<Integer> old) {

        LinkedList<Integer> finalDecodedMessage = new LinkedList<>();
        int previousBit = 0;
        for(int i = 0; i < old.size(); ++i) {
            if(old.get(i) == previousBit)
                finalDecodedMessage.add(0);
            else
                finalDecodedMessage.add(1);
            previousBit = old.get(i);
        }
        return finalDecodedMessage;
    }

    // Will combineBits the upper and lower bits to
    // have the right binary value and return the LinkedList
    public static LinkedList<Integer> combineBits(LinkedList<Integer> bits) {
        LinkedList<Integer> merged = new LinkedList<>();
        for(int i = 0; i < bits.size(); i += 2) {
            int upper = bits.get(i);
            int lower = bits.get(i + 1);
            upper = (16 * upper) + lower;
            merged.add(upper);
        }
        return merged;
    }

    // Will send the message as an array of bytes
    // as well as print them out in Hex for the user to see what was send
    public static void talkToServer(Socket socket, LinkedList<Integer> message) {
        try {
            int size = message.size();
            OutputStream os = socket.getOutputStream();
            byte[] send = new byte[message.size()];

            System.out.print("Received 32 bytes: ");
            for(int i = 0; i < size; ++i) {
                int current = message.get(i);
                System.out.print(Integer.toHexString(current).toUpperCase());
                send[i] = (byte) current;
            }
            System.out.println();
            os.write(send);

        } catch (Exception e) { e.printStackTrace(); }
    }

    // Checks response and replies with true if
    // it is good or false if it is bad
    public static boolean checkResponse(Socket socket) {
        try {
            InputStream is = socket.getInputStream();
            int response = is.read();
            return ( response == 1 ) ? true : false;
        } catch (Exception e) { e.printStackTrace(); }
        return false;
    }


    // Instantiating a Conversion convTable class to make it easier to compile and run
    // instead of having a Conversion convTable class
    static final class ConversionTable {
        HashMap<String, String> convTable;

        public ConversionTable() {
            convTable = new HashMap<String, String>();
            convTable.put("11110", "0000");
            convTable.put("10010", "1000");
            convTable.put("01001", "0001");
            convTable.put("10011", "1001");
            convTable.put("10100", "0010");
            convTable.put("10110", "1010");
            convTable.put("10101", "0011");
            convTable.put("10111", "1011");
            convTable.put("01010", "0100");
            convTable.put("11010", "1100");
            convTable.put("01011", "0101");
            convTable.put("11011", "1101");
            convTable.put("01110", "0110");
            convTable.put("11100", "1110");
            convTable.put("01111", "0111");
            convTable.put("11101", "1111");
        }

        public int fiveBitConvKey(LinkedList<Integer> fiveSig) {
            String key = valueToString(fiveSig);
            return Integer.parseInt(convTable.get(key), 2);
        }

        // Congerts the fiveSig to a String to be used as the Key
        private String valueToString(LinkedList<Integer> fiveSig) {
            String result = "";
            for(Integer i : fiveSig) {
                result += i + "";
            }
            return result;
        }

    }

}