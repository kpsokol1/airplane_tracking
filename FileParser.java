package com.planes.kyle;

import java.io.*;
import java.sql.*;

class FileParser {

    private static BufferedReader reader = null;

    static void parseTextFile(Connection conn, String fileName, final String table, String delimiter) {
        String line;
        Statement stmt;
        String sql;

        try {
            //Get connection info
            stmt = conn.createStatement();

            sql = "DROP TABLE IF EXISTS `" + table + "`";
            stmt.executeUpdate(sql);

            sql = "CREATE TABLE `" + table + "` (id INTEGER not NULL) DEFAULT CHARSET=latin1";
            stmt.executeUpdate(sql);

            normalizeLineEndings(fileName);
            reader = new BufferedReader(new FileReader(fileName));

            line = reader.readLine();
            String[] fieldNames = line.split(delimiter);
            for (String colName : fieldNames) {
                sql = "ALTER TABLE `" + table + "` ADD COLUMN`" + colName.replace("\"", "") + "` VARCHAR (255)";
                stmt.executeUpdate(sql);
            }

            //remove placeholder id column
            sql = "ALTER TABLE `" + table + "` DROP COLUMN id";
            stmt.executeUpdate(sql);

            //Add data to each column in DB
            /*sql = " LOAD DATA LOCAL INFILE '" + fileName +
                    "' INTO TABLE `" + table + "`" +
                    " CHARACTER SET latin1" +
                    " FIELDS TERMINATED BY '" + delimiter + "'" +
                    " LINES TERMINATED BY '\\r\\n'" +
                    " IGNORE 1 LINES";*/

            sql = " LOAD DATA LOCAL INFILE '" + fileName +
                    "' INTO TABLE `" + table + "`" +
                    " CHARACTER SET latin1" +
                    " FIELDS TERMINATED BY '" + delimiter + "' OPTIONALLY ENCLOSED BY '" + "\"'" +
                    " LINES TERMINATED BY '\\r\\n'" +
                    " IGNORE 1 LINES";
            stmt.executeUpdate(sql);
        }
        catch (IOException | SQLException e) {
            System.out.println(e.getMessage());
            System.out.println("FP1");
        }
        finally {
            try {
                reader.close();
            }
            catch (Exception e) {
                System.out.println(e.getMessage());
                System.out.println("FP2");
            }
        }
    }

    private static void normalizeLineEndings(String fileName) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
            String line = reader.readLine();

            DataOutputStream writeLine = new DataOutputStream(new FileOutputStream("file.tmp"));

            while (line != null) {
                line += "\r\n";
                writeLine.write(line.getBytes());
                line = reader.readLine();
            }
            reader.close();
            writeLine.close();

            //Remove the original file and rename the temp file
            File file = new File("file.tmp");
            File origFile = new File(fileName);
            if (origFile.delete())
                file.renameTo(origFile);
        }
        catch (IOException e) {
            System.out.println(e.getMessage());
            System.out.println("FP1");
        }
    }
}
