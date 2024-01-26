import java.io.IOException;

public class AnaplanExportExample {

    public static void main(String[] args) {
        try {
            // Replace these values with your actual Anaplan credentials and details
            String workspaceId = "";
            String modelId = "";
            String exportId = "";
            String exportFilePath = "";
            
            // Anaplan User
            String username = "";
            String password = "";
            

            // Anaplan Authentication
            String authToken = AnaplanFunctions.getAuthTokenBasic(workspaceId, username, password);
            // Run export and get the exported file
            String exportedFile = AnaplanFunctions.runExport(authToken, workspaceId, modelId, exportId, exportFilePath);

            // Display the result (replace with your actual processing logic)
            System.out.println("Exported file content:\n" + exportedFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return;
    }
}
