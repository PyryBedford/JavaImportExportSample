import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AnaplanFunctions {

    private static final String BASE_URL = "https://api.anaplan.com/2/0/workspaces/";

    public static String getAuthTokenBasic(String workspaceId, String userName, String password) throws Exception {
        String user = "Basic " + Base64.getEncoder().encodeToString((userName + ":" + password).getBytes(StandardCharsets.UTF_8));
        String authUrl = "https://auth.anaplan.com/token/authenticate";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(authUrl))
                .header("Authorization", user)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        String authToken = response.body();
        if (authToken.contains("tokenValue")) {
            return authToken.split("\"tokenValue\":\"")[1].split("\"")[0];
        } else {
            throw new Exception("No Token Info found - check your credentials?");
        }
    }

    public static void importDFActionSingleChunk(String workspaceId, String modelId, String action,
                                                 String fileId, String filename, String authToken, String dfCsv) throws Exception {
        // Replace with your file metadata
        String fileUrl = String.format("https://api.anaplan.com/2/0/workspaces/%s/models/%s/files/%s", workspaceId, modelId, fileId);

        HttpRequest uploadRequest = HttpRequest.newBuilder()
                .uri(URI.create(fileUrl))
                .header("Authorization", "AnaplanAuthToken " + authToken)
                .header("Content-Type", "application/octet-stream")
                .PUT(HttpRequest.BodyPublishers.ofString(dfCsv))
                .build();

        HttpResponse<String> uploadResponse = HttpClient.newHttpClient().send(uploadRequest, HttpResponse.BodyHandlers.ofString());

        if (uploadResponse.statusCode() == 200) {
            System.out.println("File Upload Successful.");
        } else {
            System.out.println("There was an issue with your file upload: " + uploadResponse.statusCode());
        }

        // Trigger import
        String importUrl = String.format("https://api.anaplan.com/2/0/workspaces/%s/models/%s/imports/%s/tasks", workspaceId, modelId, action);

        HttpRequest importRequest = HttpRequest.newBuilder()
                .uri(URI.create(importUrl))
                .header("Authorization", "AnaplanAuthToken " + authToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"localeName\":\"en_US\"}"))
                .build();

        HttpResponse<String> importResponse = HttpClient.newHttpClient().send(importRequest, HttpResponse.BodyHandlers.ofString());

        System.out.println(importResponse.statusCode());
    }


    public static String getExportIdWithName(String token, String workspaceId, String modelId, String exportName) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + workspaceId + "/models/" + modelId + "/exports"))
                .header("Authorization", "AnaplanAuthToken " + token)
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        String foundId = null;
        for (String export : response.body().split("\"exports\":\\[")[1].split("\\},\\{")) {
            if (export.contains("\"name\":\"" + exportName + "\"")) {
                String[] exportInfo = export.split("\"id\":\"");
                if (exportInfo.length > 1) {
                    foundId = exportInfo[1].split("\"")[0];
                    break;
                }
            }
        }

        return foundId;
    }


    public static String runExport(String token, String workspaceId, String modelId, String exportId, String exportFilePath) throws IOException, InterruptedException {


        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + workspaceId + "/models/" + modelId + "/exports/" + exportId + "/tasks"))
                .header("Authorization", "AnaplanAuthToken " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"localeName\": \"en_US\"}"))
                .build();

        System.out.println(request.uri().toString());

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        String taskId = "";
		try {
			taskId = extractTaskId(response.body());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

        waitExportCompleted(token, workspaceId, modelId, exportId, taskId);

        try {
        	loadFile(token, workspaceId, modelId, exportId, exportFilePath);
			return "";
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return "";
		}
    }

    public static void waitExportCompleted(String token, String workspaceId, String modelId, String exportId, String taskId) throws IOException, InterruptedException {
        String url = BASE_URL + workspaceId + "/models/" + modelId + "/exports/" + exportId + "/tasks/" + taskId;

        while (true) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "AnaplanAuthToken " + token)
                    .GET()
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            System.out.print(response.body());
            if (response.body().contains("COMPLETE")) {
                break;
            }

            System.out.println("waiting on " + workspaceId + " " + modelId + " " + taskId + " to complete.");
        }
    }

    public static void loadFile(String token, String workspaceId, String modelId, String fileId, String outputFilePath) throws IOException, InterruptedException {

        String octetHeaders = "octet";
        String jsonHeaders = "";

        String url = BASE_URL + workspaceId + "/models/" + modelId + "/files/" + fileId + "/chunks";

        HttpRequest chunksRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "AnaplanAuthToken " + token)
                .header("Content-Type", "application/json")
                .GET()
                .build();

        HttpResponse<String> chunksResponse = HttpClient.newHttpClient().send(chunksRequest, HttpResponse.BodyHandlers.ofString());

        String chunks = chunksResponse.body();
        System.out.println(chunks);
        List<Chunk> chunkList = new ArrayList<>();
        
        try {
			chunkList = extractChunks(chunks);
		} catch (Exception e) {
			e.printStackTrace();
		}
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath))) {
            for (Chunk chunk : chunkList) {
                String chunkId = chunk.getId();
                System.out.println("Getting chunk " + chunkId);

                HttpRequest getChunkRequest = HttpRequest.newBuilder()
                        .uri(URI.create(url + "/" + chunkId))
                        .header("Authorization", "AnaplanAuthToken " + token)
                        .header("Content-Type", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> getChunkResponse = HttpClient.newHttpClient().send(getChunkRequest, HttpResponse.BodyHandlers.ofString());

                String csvText = getChunkResponse.body();  // Assuming CSV is encoded in UTF-8

                // Write CSV text to file
                writer.write(csvText);
                writer.newLine();

                System.out.println("Status code: " + getChunkResponse.statusCode());
            }
        }
    }
    
    private static String extractTaskId(String jsonResponse) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(jsonResponse);

        // Navigate to the "task" node and get the value of "taskId"
        JsonNode taskNode = jsonNode.path("task");
        String taskId = taskNode.path("taskId").asText();

        return taskId;
    }
    
    private static List<Chunk> extractChunks(String jsonResponse) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(jsonResponse);

        List<Chunk> chunkList = new ArrayList<>();

        if (jsonNode.has("chunks")) {
            JsonNode chunksNode = jsonNode.path("chunks");
            for (JsonNode chunkNode : chunksNode) {
                String id = chunkNode.path("id").asText();
                String name = chunkNode.path("name").asText();
                chunkList.add(new Chunk(id, name));
            }
        }

        return chunkList;
    }

    // Define a simple Chunk class to represent the structure of each chunk
    static class Chunk {
        private String id;
        private String name;

        public Chunk(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }
}
