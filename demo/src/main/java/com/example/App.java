package com.example;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Scanner;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

/**
 * Bowling Case 
 * https://github.com/skat/bowling-opgave
 */
public class App 
{
    public static void main( String[] args ) throws Exception
    {
        //request
        final URL bowlingURL = new URL("http://13.74.31.101/api/points");
        HttpURLConnection bowlingConnection = connectionRequestSetup(bowlingURL);
        
        int bowlingConnectionStatus = bowlingConnection.getResponseCode();
        if (bowlingConnectionStatus != 200)
            throw new Exception("REST request 1 failed");

        String incoming = scanResponse(bowlingURL);

        JsonObject requestResult = new Gson().fromJson(incoming, JsonObject.class);
        String token = requestResult.get("token").toString();

        bowlingConnection.disconnect();
        
        JsonArray pointsJsonArr = requestResult.getAsJsonArray("points");
        JsonArray results =  calculateSummedScores(pointsJsonArr);

        //TODO post with success
        CloseableHttpClient httpClient = HttpClientBuilder.create().build();
        HttpPost post = postSetup(bowlingURL.toURI(), new BowlingPost(token, results));

        try {
            HttpResponse  response = httpClient.execute(post);
            System.out.println(response.getStatusLine() + " - " + EntityUtils.toString(response.getEntity()));
        } catch (Exception e){
            throw new Exception("REST failure",e);
        }

        testCalculator();
    }

    //calculates summed scores from bowling scoreboard
    private static JsonArray calculateSummedScores (JsonArray scoreBoard) {
        JsonArray result = new JsonArray();
        int runningScore = 0;
        
        //calculate score
        for (int i = 0; i < scoreBoard.size(); i++) {
            JsonArray frame = scoreBoard.get(i).getAsJsonArray();
            
            int point1 = frame.get(0).getAsInt();
            int point2 = frame.get(1).getAsInt();
            
            if (point1 == 10) {
                //strike
                runningScore += point1 + getNext2Points(i, scoreBoard);
                result.add(runningScore);
            } else if (point1 + point2 == 10) {
                //spare
                runningScore += point1 + point2 + getNextPoint(i, scoreBoard);
                result.add(runningScore);
            } else {
                runningScore += point1 + point2;
                result.add(runningScore);
            }
        }
        return result;
    }

    //gets next point if available - used for calculating Spare bonus scores
    private static int getNextPoint(int i, JsonArray scoreBoard) {
        if (i + 1 >= scoreBoard.size())
            return 0;
        return scoreBoard.get(i + 1).getAsJsonArray().get(0).getAsInt();
    }

    //gets next 2 points if available - used for Strike bonus scores
    private static int getNext2Points(int i, JsonArray scoreBoard) {
        if (i + 1 >= scoreBoard.size())
            return 0;
        if (scoreBoard.get(i + 1).getAsJsonArray().get(0).getAsInt() == 10) { //next score i strike
            if (i + 2 >= scoreBoard.size()) {
                return 10;
            } else {
                return scoreBoard.get(i + 1).getAsJsonArray().get(0).getAsInt()
                     + scoreBoard.get(i + 2).getAsJsonArray().get(0).getAsInt();
            }
        } else {
            return scoreBoard.get(i + 1).getAsJsonArray().get(0).getAsInt()
                 + scoreBoard.get(i + 1).getAsJsonArray().get(1).getAsInt();
        }
    }

    //scans response from GET request and returns result
    private static String scanResponse (URL url) throws IOException {
        String s = "";
        Scanner scanner = new Scanner(url.openStream());

        while (scanner.hasNext()) {
            s += scanner.nextLine();
        }
        scanner.close();

        return s;
    }

    //setup for POST request
    private static HttpPost postSetup (URI uri, BowlingPost bowlingPost) {
        Gson gson = new Gson();
        HttpPost post = new HttpPost(uri);
        StringEntity postString = new StringEntity(gson.toJson(bowlingPost), "UTF-8");
        postString.setContentType("application/json");
        post.setHeader("Content-type", "application/json");
        post.setEntity(postString);

        return post;
    }

    //setup for GET request
    private static HttpURLConnection connectionRequestSetup (URL connUrl) throws IOException {
        
        HttpURLConnection connect = (HttpURLConnection) connUrl.openConnection();
        connect.setConnectTimeout(2500);
        connect.setReadTimeout(2500);
        connect.setInstanceFollowRedirects(false);

        return connect;
    }

    private static void testCalculator() {
        //test calculation with simple points (no spare or strike)
        JsonArray simpleScore = new JsonArray();
        JsonArray simpleScoreFrame0 = new JsonArray();
        simpleScoreFrame0.add(3);
        simpleScoreFrame0.add(4);
        simpleScore.add(simpleScoreFrame0);
        JsonArray simpleScoreFrame1 = new JsonArray();
        simpleScoreFrame1.add(5);
        simpleScoreFrame1.add(4);
        simpleScore.add(simpleScoreFrame1);
        JsonArray simpleScoreResult = calculateSummedScores(simpleScore);
        if (simpleScoreResult.get(1).getAsInt() != 16)
            System.out.println("Error in simpleScore calculation");

        //test calculation with full strike score - perfect game
        JsonArray fullStrike = new JsonArray();
        JsonArray strikeFrame = new JsonArray();
        strikeFrame.add(10);
        strikeFrame.add(0);
        JsonArray lastStrikeFrame = new JsonArray();
        lastStrikeFrame.add(10);
        lastStrikeFrame.add(10);
        
        for(int i = 0; i < 10; i++) {
            fullStrike.add(strikeFrame);
        }
        fullStrike.add(lastStrikeFrame);

        JsonArray fullStrikeResult = calculateSummedScores(fullStrike);
        if (fullStrikeResult.get(10).getAsInt() != 300)
            System.out.println("Error in fullstrike calculation");

        //test calculation with score including spares
        JsonArray scoreWithSpares = new JsonArray();
        scoreWithSpares.addAll(simpleScore);
        JsonArray spareFrame2 = new JsonArray();
        spareFrame2.add(7);
        spareFrame2.add(3);
        scoreWithSpares.add(spareFrame2);
        JsonArray spareFrame3 = new JsonArray();
        spareFrame3.add(5);
        spareFrame3.add(5);
        scoreWithSpares.add(spareFrame3);
        JsonArray spareFrame4 = new JsonArray();
        spareFrame4.add(0);
        spareFrame4.add(6);
        scoreWithSpares.add(spareFrame4);

        JsonArray spareResult = calculateSummedScores(scoreWithSpares);
        if (spareResult.get(4).getAsInt() != 47)
            System.out.println("Error in spare calculation");

    }
}

//class used for result sent in POST request
class BowlingPost {
    private String token;
    private JsonArray points;
    public BowlingPost(String token, JsonArray points) {
        this.token = token;
        this.points = points;
    }
    public String getToken() {
        return token;
    }
    public JsonArray getPoints() {
        return points;
    }
    public void setPoints(JsonArray points) {
        this.points = points;
    }
    public void setToken(String token) {
        this.token = token;
    }
}