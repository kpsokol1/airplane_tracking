package com.planes.kyle;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.planes.kyle.Parser.*;

class Utilities {

    static boolean wasNull = false;
    static boolean writeToCleanDataTable(HashMap<String, String> aircraftData, HashMap<String, HashMap<String, String>> gerecentPlanes) {
        String ICAO = aircraftData.get("ICAO");

        //Ignore record if it starts with "~"
        if (!ICAO.startsWith("~")) {
            //String filePath = "C:/Users/kyle/Documents";
            String filePath = "/home/pi/Program";

            String planeRegistration = "";
            String query = "SELECT registration FROM Aircrafts WHERE icao24 ='" + ICAO + "'";

            String callsign = aircraftData.get("Callsign");
            String aircraft = aircraftData.get("Aircraft");
            String airline = aircraftData.get("Airline");

            //Email Stuff:
            String recipient = "sokolky20@stxtigers.com";
            String subject1 = currentAirline + " Plane Alert";
            String subject3 = aircraft + " Plane Alert";
            String message = currentAirline + "\nCallsign: " + callsign + "\nType: " + aircraft + "\nFlightRadar24: " + "https://www.flightradar24.com/" + callsign;
            String attachmentLocation = filePath +"/image.jpg";

            try {
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(query);
                if (rs.next()) {
                    planeRegistration = rs.getString("registration");
                }
                st.close();
            } catch (Exception e) {
                System.out.println(e.getMessage());
                e.printStackTrace();
            }
            String subject2 = planeRegistration + " Plane Alert";

            boolean alreadySent = false;

            //Check if have seen plane recently
            if (recentPlanes.containsKey(ICAO) && getTimeDifference(recentPlanes.get(ICAO).get("Time")) < timePlaneStillAlive) {
                //If callsign was previously null and now callsign is not null add the callsign to the record
                if (recentPlanes.get(ICAO).get("Callsign") == null && callsign != null) {
                    recentPlanes.put(aircraftData.get("ICAO"), aircraftData);
                    alreadySent = photoAndEmail(callsign, AirlineIdentify.emailList, filePath,recipient,subject1,message,attachmentLocation,planeRegistration);
                    try {
                        if (!alreadySent) {
                            alreadySent = photoAndEmail(planeRegistration, AirlineIdentify.emailTailNumbersList, filePath,recipient,subject2,message,attachmentLocation,planeRegistration);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (!alreadySent) {
                            alreadySent = photoAndEmail(planeRegistration,AirlineIdentify.emailPlanesList, filePath,recipient,subject3,message,attachmentLocation,planeRegistration);
                    }
                    updateRecord = true;
                    return true;
                }
                //update recent planes anyways //don't write to database
                else {
                    if (callsign == null) {
                        try {
                            String _query = "SELECT Callsign FROM `" + RAW_DATA_TABLE + "` WHERE Callsign IS NOT NULL AND ICAO = '" + aircraftData.get("ICAO")+"' AND Time > '" + toLocalDateTime((LocalDateTime.parse(aircraftData.get("Time"), DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")).toEpochSecond(ZoneOffset.UTC)) - timePlaneStillAlive) + "' ORDER BY TIME desc limit 1";
                            Statement st = conn.createStatement();
                            ResultSet rs = st.executeQuery(_query);
                            if (rs.next()) {
                                aircraftData.put("Callsign", rs.getString("Callsign"));
                                String _query1 = "SELECT Airline as Airline FROM `" + RAW_DATA_TABLE + "` WHERE Airline != \'No Info\' AND ICAO = '" + aircraftData.get("ICAO")+"' AND Time > '" + toLocalDateTime((LocalDateTime.parse(aircraftData.get("Time"), DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")).toEpochSecond(ZoneOffset.UTC)) - timePlaneStillAlive) + "' ORDER BY TIME desc limit 1";
                                Statement _st1 = conn.createStatement();
                                ResultSet _rs2 = _st1.executeQuery(_query1);
                                if(_rs2.next()){
                                    aircraftData.replace("Airline", _rs2.getString("Airline"));
                                    _st1.close();
                                }
                                recentPlanes.put(aircraftData.get("ICAO"),aircraftData);
                                updateRecord = true;
                                wasNull = true;
                                st.close();
                                return true;
                            }

                        } catch (SQLException e) {
                            System.out.println(e.getMessage());
                            e.printStackTrace();
                        }
                    }
                    recentPlanes.put(ICAO, aircraftData);
                    return false;
                }
            }
            //New plane so add to DB
            else {
                recentPlanes.put(ICAO, aircraftData);
                //can't do .contains on a null object{
                if (currentAirline != null && callsign != null) {
                    alreadySent = photoAndEmail(callsign, AirlineIdentify.emailList, filePath,recipient,subject1,message,attachmentLocation,planeRegistration);

                    try {
                        if (!alreadySent) {
                            if (planeRegistration != null && !planeRegistration.equals("") && !planeRegistration.equals(",")) {
                                alreadySent = photoAndEmail(planeRegistration, AirlineIdentify.emailTailNumbersList, filePath,recipient,subject2,message,attachmentLocation, planeRegistration);
                            }
                        }
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (!alreadySent) {
                        alreadySent = photoAndEmail(aircraft, AirlineIdentify.emailPlanesList, filePath,recipient,subject3,message,attachmentLocation, planeRegistration);
                    }
                }
            }
            return true;
        }
        return false;
    }

    static void purgeRecentPlanesList(HashMap<String, HashMap<String, String>> recentPlanes) {
        Iterator <Map.Entry<String, HashMap<String, String>>> it = recentPlanes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry <String, HashMap<String, String>> pair = it.next();
            if (getTimeDifference(pair.getValue().get("Time")) > timePlaneStillAlive) {
                it.remove();
            }
        }
    }

    static long getTimeDifference(String lastSeenTime) {
        LocalDateTime lastSeen = LocalDateTime.parse(lastSeenTime, DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"));
        LocalDateTime currentTime = LocalDateTime.now();

        return currentTime.toEpochSecond(ZoneOffset.UTC) - lastSeen.toEpochSecond(ZoneOffset.UTC);
    }

    static String toLocalDateTime(long second) {
        Instant instant = Instant.ofEpochSecond(second);
        return dtf.format(instant.atZone(ZoneOffset.UTC).toLocalDateTime());
    }
    private static boolean photoAndEmail(String type, String[] list, String filePath, String recipient, String subject, String message, String attachmentLocation, String registration) {
        for (int i = 0; i < list.length; i++) {
            if (type.contains(list[i])) {
                if(registration != null && !registration.equals("")){
                    if(!Scraper.getPhoto(registration, filePath)){
                        attachmentLocation = "none";
                    }
                }
                else{
                    attachmentLocation = "none";
                }
                Email.sendEmail(recipient, subject, message, attachmentLocation);
                return true;
            }
        }
        return false;
    }
    /*static HashMap<String, Double> circleParameters = new HashMap<>();
    static HashMap<String, Double> lineParameters = new HashMap<>();
    static void createGeoFence(double lat, double lon, double radius){
        circleParameters.put("Lat",lat); //center x-coordinate
        circleParameters.put("Lon",lon); //center y-coordinate
        circleParameters.put("Radius",Math.pow(radius, 2)); //radius
    }
    private static void createLine(double lat, double lon, double heading){
        //shift points and convert to x,y plane
        lineParameters.put("X", 90-lat); //x-coordinate
        lineParameters.put("Y", lon); //y-coordinate
        lineParameters.put("Slope", heading); //slope
    }
    private static boolean doesIntersect(HashMap<String,Double> circleParameters, HashMap<String,Double> lineParameters){
        createLine();
        double x1 = lineParameters.get("X");
        double x2 = x1 + 1;
        double y1 = lineParameters.get("Y");
        double y2 = (lineParameters.get("Y") + lineParameters.get("Slope"));
        double dx = x2 - x1;
        double dy =  y2 - y1;
        double dr = Math.sqrt((Math.pow(dx,2))+Math.pow(dy,2));
        double d = x1*y2 - x2*y1;
        double discriminant = circleParameters.get("Radius") * Math.pow(dr,2) - Math.pow(d,2);
        if(discriminant >= 0){
            return true;
        }
        return false;
    }*/


    static String getAuth(String key) {
        try {
            Properties properties = new Properties();
            properties.load(new FileInputStream(new File(CREDENTIALS_FILE_NAME)));

            return properties.getProperty(key);
        }
        catch(IOException e) {
            System.out.println(e.getMessage());
            System.out.println("Utilities1");
        }
        return null;
    }

    private static void setPK(String table, String pk) {
        Statement stmt = null;
        try {
            //Set pk for table
            String sql;
            String currentPK = "";
            stmt = conn.createStatement();
            ResultSet rs = conn.getMetaData().getPrimaryKeys(null, null, table);

            if (rs.next()) {
                currentPK = rs.getString("COLUMN_NAME");
            }

            //Do not add primary key if already created
            if (!currentPK.equals(pk)) {
                sql = "ALTER TABLE `" + table + "` ADD PRIMARY KEY (`" + pk + "`)";
                stmt.executeUpdate(sql);
            }
        }
        catch(SQLException e) {
            System.out.println(e.getMessage());
            System.out.println("Utilities2");
        }
        finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            }
            catch (Exception e) {
                System.out.println(e.getMessage());
                System.out.println("Utilities1");
            }
        }
    }
}
