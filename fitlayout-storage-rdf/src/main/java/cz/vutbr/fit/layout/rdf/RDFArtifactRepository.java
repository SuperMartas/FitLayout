/**
 * RDFArtifactRepository.java
 *
 * Created on 30. 9. 2020, 15:00:47 by burgetr
 */
package cz.vutbr.fit.layout.rdf;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.query.Binding;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.vutbr.fit.layout.api.ArtifactRepository;
import cz.vutbr.fit.layout.model.Artifact;
import cz.vutbr.fit.layout.ontology.BOX;
import cz.vutbr.fit.layout.ontology.SEGM;

/**
 * Implementation of an ArtifactRepository on top of a RDFStorage.
 * 
 * @author burgetr
 */
public class RDFArtifactRepository implements ArtifactRepository
{
    private static Logger log = LoggerFactory.getLogger(RDFArtifactRepository.class);
    
    private RDFStorage storage;
    private Map<IRI, ModelBuilder> modelBuilders;
    private Map<IRI, ModelLoader> modelLoaders;

    
    public RDFArtifactRepository(RDFStorage storage)
    {
        this.storage = storage;
        modelBuilders = new HashMap<>();
        modelLoaders = new HashMap<>();
        initDefaultModelBuilders();
    }

    public RDFStorage getStorage()
    {
        return storage;
    }

    //Artifact functions =============================================================
    
    @Override
    public Collection<IRI> getArtifactIRIs() throws RepositoryException
    {
        final String query = storage.declarePrefixes()
                + "SELECT ?pg "
                + "WHERE {"
                + "  ?pg rdf:type ?type . "
                + "  ?type rdfs:subClassOf layout:Artifact "
                + "}";
        
        log.debug("QUERY: {}", query);
        TupleQueryResult data = storage.executeSafeTupleQuery(query);
        Set<IRI> ret = new HashSet<IRI>();
        try
        {
            while (data.hasNext())
            {
                BindingSet binding = data.next();
                Binding b = binding.getBinding("pg");
                ret.add((IRI) b.getValue());
            }
        } catch (QueryEvaluationException e) {
            e.printStackTrace();
        }
        return ret;
    }
    
    @Override
    public Artifact getArtifact(IRI artifactIri)
    {
        Model model = getArtifactModel(artifactIri);
        IRI type = getArtifactType(model, artifactIri);
        if (type != null)
        {
            ModelLoader loader = getModelLoader(type);
            if (loader != null)
            {
                Artifact artifact = loader.loadArtifact(artifactIri, this);
                return artifact;
            }
            else
                log.warn("No loader available for type {}", type);
        }
        else
            log.warn("Artifact {} has no type", artifactIri);
        return null;
    }
    
    public Model getArtifactModel(IRI artifactIri)
    {
        //we use a separate context for each artifact
        return storage.getContextModel(artifactIri);
    }

    @Override
    public void addArtifact(Artifact artifact)
    {
        if (artifact.getIri() == null)
            artifact.setIri(createArtifactIri(artifact));
        
        log.debug("STORING {}", artifact);
        ModelBuilder builder = getModelBuilder(artifact.getArtifactType());
        if (builder != null)
        {
            Model graph = builder.createGraph(artifact);
            storage.insertGraph(graph, artifact.getIri());
        }
        else
            log.error("Could not find RDF model builder for artifact {}, type {}", artifact, artifact.getArtifactType());
    }

    @Override
    public IRI createArtifactIri(Artifact artifact)
    {
        long seq = storage.getNextSequenceValue("page");
        IRI pageUri = RESOURCE.createArtifactIri(seq);
        return pageUri;
    }
    
    @Override
    public void removeArtifact(IRI artifactIri)
    {
        storage.clear(artifactIri);
    }

    @Override
    public void clear()
    {
        storage.clear();
    }

    /**
     * Gets the artifact type from an artifact model.
     * @param model the artifact model
     * @param artifactIri the artifact IRI
     * @return the type IRI or {@code null} when no type declaration (rdf:type) was found.
     */
    private IRI getArtifactType(Model model, IRI artifactIri)
    {
        Iterable<Statement> typeStatements = model.getStatements(artifactIri, RDF.TYPE, null);
        for (Statement st : typeStatements)
        {
            if (st.getObject() instanceof IRI)
                return (IRI) st.getObject();
        }
        return null; //no type statement found
    }
    
    //Model builders =================================================================

    protected void initDefaultModelBuilders()
    {
        addModelBuilder(BOX.Page, new BoxModelBuilder());
        addModelBuilder(SEGM.AreaTree, new AreaModelBuilder());
        addModelBuilder(SEGM.LogicalAreaTree, new LogicalAreaModelBuilder());
        addModelLoader(BOX.Page, new BoxModelLoader());
        addModelLoader(SEGM.AreaTree, new AreaModelLoader());
        addModelLoader(SEGM.LogicalAreaTree, new LogicalAreaModelLoader());
    }
    
    public void addModelBuilder(IRI artifactType, ModelBuilder builder)
    {
        modelBuilders.put(artifactType, builder);
    }
    
    public ModelBuilder getModelBuilder(IRI artifactType)
    {
        return modelBuilders.get(artifactType);
    }
    
    public void addModelLoader(IRI artifactType, ModelLoader builder)
    {
        modelLoaders.put(artifactType, builder);
    }
    
    public ModelLoader getModelLoader(IRI artifactType)
    {
        return modelLoaders.get(artifactType);
    }

}