package ru.ifmo.ctddev.fedotov.walk;

import javax.xml.bind.DatatypeConverter;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by aleksandr on 29.10.2016.
 */
public class Walk {
    public static final String DEFAULT_HASH = "00000000000000000000000000000000";
    private static final String UTF_8 = "UTF8";

    static class EmptyArgsException extends Exception {
        @Override
        public String getMessage() {
            return "Incorrects args: inputfile outputfile";
        }
    }

    static class Arguments {
        private String inputFile;
        private String outputFile;

        public Arguments(String inputFile, String outputFile) {
            this.inputFile = inputFile;
            this.outputFile = outputFile;
        }

        public String getInputFile() {
            return inputFile;
        }

        public String getOutputFile() {
            return outputFile;
        }
    }

    public static void main(String[] args) {

        try {

            Arguments arguments = resolveArgs(args);
            List<String> fileNames = loadFileNames(arguments);
            walk(fileNames, arguments);

        } catch (Throwable e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    private static void walk(List<String> fileNames, Arguments arguments) {
        String hash;
        StringBuilder result = new StringBuilder();
        try (BufferedWriter br = new BufferedWriter(
                new OutputStreamWriter(
                        new FileOutputStream(arguments.getOutputFile()),
                        UTF_8)
        )) {
            for (String filename : fileNames) {
                hash = getFileHashCode(filename);
                result.append(hash)
                        .append(' ')
                        .append(filename);
                writeToFile(result.toString(), br);
                result.setLength(0);
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    private static String getFileHashCode(String filename) {
        String hash = DEFAULT_HASH;
        byte[] tmp = new byte[1024];
        Integer read;
        try (FileInputStream fis = new FileInputStream(filename)) {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            FileChannel channel = fis.getChannel();
            ByteBuffer buff = ByteBuffer.allocate(1024);
            while (channel.read(buff) != -1) {
                buff.flip();
                md5.update(buff);
                buff.clear();
            }
            hash = DatatypeConverter.printHexBinary(md5.digest());
        } catch (IOException e) {
            System.out.println("Not coorect path or file not exist: " + filename);
        } catch (NoSuchAlgorithmException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        return hash;
    }


    private static List<String> loadFileNames(Arguments arguments) {
        List<String> fileNames = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(
                        new FileInputStream(arguments.getInputFile()),
                        UTF_8
                ))
        ) {
            String line = br.readLine();
            while (line != null) {
                fileNames.add(line);
                line = br.readLine();
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        return fileNames;
    }

    private static void writeToFile(String line, BufferedWriter bufferedWriter) throws IOException {
        bufferedWriter.write(line);
        bufferedWriter.newLine();
    }

    protected static Arguments resolveArgs(String[] args) throws EmptyArgsException {
        if (args.length != 2) {
            throw new EmptyArgsException();
        }
        Arguments arg = new Arguments(args[0], args[1]);
        return arg;
    }
}
