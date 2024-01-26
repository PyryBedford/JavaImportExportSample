import java.nio.file.Files;
import java.nio.file.Paths;

public class AnaplanImportExample {

    public static void main(String[] args) throws Exception {
        // These values to be requested from Anaplan Model Builder team
        String workspaceId = "8a81b08e5e8c6d35015eb9b41cf85ba8";
        String modelId = "20A81F2BC2FF4861A78AF40620C037FE";
        String action = "112000000000";
        String fileId = "113000000000";
        String filename = "import_sample.csv";

        // Anaplan User
        String username = "ppeura@bedfordconsulting.com";
        String password = "tKDJ1avTW6E^$7WE";

        // Anaplan Authentication
        String authToken = AnaplanFunctions.getAuthTokenBasic(workspaceId, username, password);

        // Read CSV file
        String csvData = Files.readString(Paths.get("C:\\Users\\PyryPeura\\eclipse-workspace\\ImportExportSample\\src\\file.csv"));

        // Write CSV to Anaplan
        AnaplanFunctions.importDFActionSingleChunk(workspaceId, modelId, action, fileId, filename, authToken, csvData);
        
        System.out.print("File imported");
    }
}
