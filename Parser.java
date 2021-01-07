package com.planes.kyle;

import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import static com.planes.kyle.DB.*;
import static com.planes.kyle.FileParser.parseTextFile;
import static com.planes.kyle.Utilities.purgeRecentPlanesList;
import static java.lang.Thread.sleep;
//C:/Users/kyle/Downloads/aircraftDatabase.csv
////C:/Users/kyle/Documents/aircraft_and_manufacturers.csv
////"C:/Users/kyle/Documents/airport-codes.csv"

public class Parser
{
    static final String RAW_DATA_TABLE = "Raw1";
    static final String CLEAN_DATA_TABLE = "Clean1";
    static final String CREDENTIALS_FILE_NAME = "credentials.properties";
    static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    static Connection conn;
    static Connection conn2;
    static Statement statement;
    static HashMap<String, HashMap<String, String>> recentPlanes = new HashMap<>();
    //Times below in seconds
    static int timePlaneStillAlive = 45*60;
    private static long refreshInterval = 5;
    static boolean updateRecord = false;
    static boolean foundAirline = false;
    private static long numberOfTimesProcessedFile = 0;
    public static String currentAirline;



    //Using a linked HashMap to maintain the order of the columns as specified below
    //This list holds the desired column name (key) and the actual column name in the aircraft JSON file (value)
    static final LinkedHashMap<String, String> columnNames = new LinkedHashMap<>();
       static
        {
            columnNames.put("Time", "time");
            columnNames.put("ICAO", "hex");
            columnNames.put("Callsign", "flight");
            columnNames.put("Ground Speed", "gs");
            columnNames.put("Heading", "track");
            columnNames.put("Latitude", "lat");
            columnNames.put("Longitude", "lon");
            columnNames.put("Geometric Altitude", "alt_geom");
            columnNames.put("Barometric Altitude", "alt_baro");
            columnNames.put("Airline", "airline"); //added airline column
            columnNames.put("Aircraft", "aircraft"); //added aircraft column
            columnNames.put("Squawk", "squawk");
            columnNames.put("Emergency", "emergency");
            columnNames.put("MLAT", "mlat");
        }
    static HashMap<String, String> prePopulatedList = new HashMap<>();
    static HashMap<String, String> tempPrePopulatedList = new HashMap<>();


    public static void main(String[]args) throws Exception
    {
        if(args.length < 1) {
            System.out.println("You did not enter any arguments!");
            return;
        }
        try {
            //Initialize database connection
            DBConnect();

            //Create tables if they do not exist in the DB
            if (!checkIfTableExists(RAW_DATA_TABLE)) createTable(RAW_DATA_TABLE);
            if (!checkIfTableExists(CLEAN_DATA_TABLE)) {
                createTable(CLEAN_DATA_TABLE);
            }
            else {
                populateRecentPlanesList();
            }

            //If pass additional file name as argument then process this file as aircraft database
            if (args.length == 2) {
                parseTextFile(conn, args[1], "Aircrafts", ","); //All Airplanes Database
                System.out.println("Done");
                return;
            }
            if(args.length == 3){
                parseTextFile(conn,args[2], "Airplane_Types", ","); //List of Aircrafts
                parseTextFile(conn, args[1], "Aircrafts", ","); //All Airplanes Database
            }
            if(args.length > 3) {
                parseTextFile(conn,args[3],"airports",","); //List of Airports
                parseTextFile(conn,args[2], "Airplane_Types", ","); //List of Aircrafts
                parseTextFile(conn, args[1], "Aircrafts", ","); //All Airplanes Database
            }

            //Read the aircraft data file (passed in with arguments)
            while(true){
                while (!conn.isClosed()) {   //while we have a connection to the database
                    statement = conn.createStatement();
                    //Utilities.createGeoFence(38.22942, -85.56894, 0.5);
                    readAircraftFile(args[0]);

                    //Wait below interval before processing file again (ms)
                    sleep(refreshInterval*1000);
                }
                while(conn.isClosed()){   //try to reconnect every minute
                    DBConnect();
                    sleep(60000);
                }
            }
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
            System.out.println("Parser1");
        }
        finally {
            //Close open connection with DB
            if (conn != null) {
                conn.close();
            }
        }
    }

    private static String getAirline(JSONObject aircraft, HashMap aircraftData, Map.Entry<String, String> col, String airlineString) throws SQLException {

        try {
            String airlineName = AirlineIdentify.AirlineMapping.get(airlineString.replaceAll("\\d.*", ""));
            if (airlineName != null) {
                aircraftData.put(col.getKey(), airlineName);
                return airlineName;
            }
            else {
                    String query = "SELECT operatoricao FROM Aircrafts WHERE icao24 ='" + aircraft.optString("hex") + "'";
                    Statement st = conn.createStatement();
                    ResultSet rs = st.executeQuery(query);
                    if(rs.next() && rs != null) {
                        airlineName = AirlineIdentify.AirlineMapping.get(rs.getString("operatoricao"));
                        if(airlineName != null && !airlineName.equals("") && !airlineName.equals(",")) {
                            aircraftData.put(col.getKey(), airlineName);
                            return airlineName;
                        }
                    }
                    st.close();
            }
        }
        catch (Exception e) {  //probably did not find it in the aircraft database

            System.out.println(e.getMessage());
            System.out.println("Parser 2");
        }

        aircraftData.put(col.getKey(), "No Info");
        return "No Info";
    }

    private static String getAircraft(String ICAO) throws SQLException {
        String query1 = "SELECT typecode FROM Aircrafts WHERE icao24 = '"+ICAO +"'";
        Statement st = conn.createStatement();
        try {
            ResultSet rs1 = st.executeQuery(query1);
            //String  hi = rs1.getString("typecode");
            //String dufus = "";
            if(rs1.next() && !rs1.getString("typecode").equals("")) {
                String query2 = "SELECT model FROM Airplane_Types WHERE ICAOCode = '"+rs1.getString("typecode")+"'";
                String  hi = rs1.getString("typecode");
                ResultSet rs2 = st.executeQuery(query2);
                if(rs2.next() && !rs2.getString("Model").equals("")) {
                    String returnSt = "";
                    String result = rs2.getString("Model");
                    for(int i = 0; i < result.length(); i++) {
                        if(result.charAt(i) == '(') {
                            st.close();
                            return returnSt;
                        }
                        else {
                            returnSt += result.charAt(i);
                        }
                    }
                    st.close();
                    return returnSt;
                }
                else {
                    String query3 = "SELECT manufacturername, model FROM Aircrafts WHERE icao24 = '"+ICAO+"'";
                    ResultSet rs3 = st.executeQuery(query3);
                    if(rs3.next() && !rs3.getString("manufacturername").equals("") && !rs3.getString("model").equals("")) {
                        return rs3.getString("manufacturername") + " " + rs3.getString("model");
                    }
                }
            }
            else {
               String query3 = "SELECT manufacturername, model FROM Aircrafts WHERE icao24 = '"+ICAO+"'";
               ResultSet rs3 = st.executeQuery(query3);
               if(rs3.next() && !rs3.getString("manufacturername").equals("") && !rs3.getString("model").equals("")) {
                   return rs3.getString("manufacturername") + " " + rs3.getString("model");
               }
               else {
                   st.close();
                   return "No Info";
               }
            }
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
        finally {
            st.close();
        }
        return "Problem";
    }

   private static void populateRecentPlanesList() throws SQLException {
        String query = "SELECT * FROM `"+CLEAN_DATA_TABLE+"` WHERE TIMESTAMPDIFF(SECOND, STR_TO_DATE(TIME, '%Y/%m/%d %H:%i:%s'),Now()) < '"+timePlaneStillAlive+"'";
        try {
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);
            while(rs.next()) {
                //For each column that has a value for this aircraft add it to the HashMap
                prePopulatedList.clear();
                for (Map.Entry<String, String> col : columnNames.entrySet()) {
                    //Make sure column exists
                    if (rs.getString(col.getKey()) != null) {
                        //Add data to HashMap
                        prePopulatedList.put(col.getKey(), rs.getString(col.getKey()));
                    }
                }
                recentPlanes.put(rs.getString("ICAO"),prePopulatedList);
            }
            st.close();
   }
        catch(SQLException e) {
            System.out.println(e.getMessage());
            System.out.println("Parser j");
            e.printStackTrace();
        }
    }

    private static void readAircraftFile(String filename) {
        String origin = "";
        String destination = "";
        try {
            File inputFile = new File(filename);
            String content = FileUtils.readFileToString(inputFile, "utf-8");

            //Object to hold entire aircraft JSON file
            JSONObject aircraftJSON = new JSONObject(content);

            //Array to hold list of aircrafts in the aircraft JSON file
            JSONArray aircraftList = aircraftJSON.getJSONArray("aircraft");

            //Loop through the array of aircrafts
            for (int i = 0; i < aircraftList.length(); i++) {
                //Object to hold a single aircraft from the aircraft array
                JSONObject aircraft = aircraftList.getJSONObject(i);

                if (aircraft.has("seen") && Double.parseDouble(aircraft.optString("seen")) <= refreshInterval) {
                    //HashMap to store column name and value for each aircraft
                    HashMap<String, String> aircraftData = new HashMap<>();

                    //For each column that has a value for this aircraft add it to the HashMap
                    for (Map.Entry<String, String> col : columnNames.entrySet()) {
                        //Make sure column exists
                        if (aircraft.has(col.getValue())) {
                            //Add data to HashMap
                            aircraftData.put(col.getKey(), aircraft.optString(col.getValue()));
                        }
                        //Add current time to record //column that does not naturally exist
                        else if (col.getKey().equals("Time")) {
                            aircraftData.put(col.getKey(), dtf.format(LocalDateTime.now()));
                        }
                        else if(col.getKey().equals("Airline")) {
                           currentAirline = getAirline(aircraft,aircraftData,col,aircraft.optString("flight"));
                        }
                        else if(col.getKey().equals("Aircraft")) {
                            aircraftData.put(col.getKey(), getAircraft(aircraftData.get("ICAO")));
                        }

                    }
                    //add the airline
                    addRecordToDBQueue(aircraftData);
                }
            }

            if (statement != null) {
                writeQueuedRecordsToDB(statement);
                numberOfTimesProcessedFile++;
                if (((numberOfTimesProcessedFile * refreshInterval) % timePlaneStillAlive) == 0) {
                    purgeRecentPlanesList(recentPlanes);
                }
            }


        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println(currentAirline);
            e.printStackTrace();
            System.out.println("Parser3");
        }
    }
}
