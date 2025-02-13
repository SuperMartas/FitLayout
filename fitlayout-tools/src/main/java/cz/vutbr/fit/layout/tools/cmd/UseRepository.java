/**
 * UseRepository.java
 *
 * Created on 15. 2. 2021, 10:54:07 by burgetr
 */
package cz.vutbr.fit.layout.tools.cmd;

import java.util.concurrent.Callable;

import org.eclipse.rdf4j.repository.RepositoryException;

import cz.vutbr.fit.layout.rdf.RDFArtifactRepository;
import cz.vutbr.fit.layout.tools.CliCommand;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * 
 * @author burgetr
 */
@Command(name = "USE", sortOptions = false, abbreviateSynopsis = true,
    description = "Opens a repository for loading or storing artifacts")
public class UseRepository extends CliCommand implements Callable<Integer>
{
    private static final String KEY_REPOSITORY = "fitlayout.rdf.repository";
    private static final String KEY_SERVER = "fitlayout.rdf.server";
    private static final String KEY_PATH = "fitlayout.rdf.path";

    enum RepositoryType { memory, local, http };
    
    @Parameters(arity = "1", index = "0", description = "Repository type: ${COMPLETION-CANDIDATES}")
    protected RepositoryType repositoryType;

    
    @Override
    public Integer call() throws Exception
    {
        try {
            
            RDFArtifactRepository repo = createArtifactRepository();
            getCli().setArtifactRepository(repo);
            
            return 0;
            
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
        } catch (RepositoryException e) {
            System.err.println("Error: " + e.getMessage());
        }
        
        return 1;
    }
    
    private RDFArtifactRepository createArtifactRepository()
    {
        String configPath = System.getProperty(KEY_PATH);
        String configServer = System.getProperty(KEY_SERVER);
        String configRepository = System.getProperty(KEY_REPOSITORY);
        RDFArtifactRepository storage = null;
        switch (repositoryType)
        {
            case memory:
                storage = RDFArtifactRepository.createMemory(null);
                System.err.println("Using rdf4j memory storage");
                break;
            case local:
                if (configPath == null)
                    throw new IllegalArgumentException(KEY_PATH + " system property is not set. Check your repository configuration.");
                String path = configPath.replace("$HOME", System.getProperty("user.home"));
                storage = RDFArtifactRepository.createNative(path);
                System.err.println("Using rdf4j native storage in " + path);
                break;
            case http:
                if (configServer == null)
                    throw new IllegalArgumentException(KEY_SERVER + " system property is not set. Check your repository configuration.");
                if (configRepository == null)
                    throw new IllegalArgumentException(KEY_REPOSITORY + " system property is not set. Check your repository configuration.");
                storage = RDFArtifactRepository.createHTTP(configServer, configRepository);
                System.err.println("Using rdf4j remote HTTP storage on " + configServer + " / " + configRepository);
                break;
        }
        return storage;
    }
    
}
