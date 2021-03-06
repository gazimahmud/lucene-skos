package at.ac.univie.mminf.luceneSKOS.solr;

/**
 * Copyright 2010 Bernhard Haslhofer 
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.util.ResourceLoader;
import org.apache.lucene.analysis.util.ResourceLoaderAware;
import org.apache.lucene.analysis.util.TokenFilterFactory;
import org.apache.solr.core.SolrResourceLoader;

import at.ac.univie.mminf.luceneSKOS.analysis.SKOSAnalyzer.ExpansionType;
import at.ac.univie.mminf.luceneSKOS.analysis.SKOSLabelFilter;
import at.ac.univie.mminf.luceneSKOS.analysis.SKOSURIFilter;
import at.ac.univie.mminf.luceneSKOS.analysis.tokenattributes.SKOSTypeAttribute.SKOSType;
import at.ac.univie.mminf.luceneSKOS.skos.SKOSEngine;
import at.ac.univie.mminf.luceneSKOS.skos.SKOSEngineFactory;

/**
 * A factory for plugging SKOS filters into Apache Solr
 */
public class SKOSFilterFactory extends TokenFilterFactory implements ResourceLoaderAware {
  private String skosFile;
  private String expansionTypeString;
  private String bufferSizeString;
  private String typeString;
  private String languageString;
  private ExpansionType expansionType;
  private SKOSType[] type;
  private SKOSEngine skosEngine;
  private int bufferSize;
  
  
  public SKOSFilterFactory(Map<String,String> args) {
    super(args);
    assureMatchVersion();
    skosFile = require(args, "skosFile");
    expansionTypeString = require(args, "expansionType");
    bufferSizeString = get(args, "bufferSize");
    typeString = get(args, "type");
    languageString = get(args, "language");
    
    System.out.println("Passed arguments: " + skosFile + " Type: "
        + expansionTypeString + " bufferSize: "
        + (bufferSizeString != null ? bufferSizeString : "Default")
        + " language: " + (languageString != null ? languageString : "All")
        + " type: " + (typeString != null ? typeString : "Default"));
  }
  
  @Override
  public void inform(ResourceLoader loader) {    
    try {
      if (skosFile.endsWith(".n3") || skosFile.endsWith(".rdf")
          || skosFile.endsWith(".ttl") || skosFile.endsWith(".zip")) {
        skosEngine = SKOSEngineFactory.getSKOSEngine(luceneMatchVersion,
            ((SolrResourceLoader)loader) .getConfigDir() + skosFile,
            languageString != null ? languageString.split(" ") : null);
      } else {
        throw new IOException(
            "Allowed file suffixes are: .n3 (N3), .rdf (RDF/XML), .ttl (TURTLE) and .zip (ZIP)");
      }
      
    } catch (IOException e) {
      throw new RuntimeException("Could not instantiate SKOS engine", e);
    }
    
    if (expansionTypeString.equalsIgnoreCase(ExpansionType.URI.toString())) {
      expansionType = ExpansionType.URI;
    } else if (expansionTypeString.equalsIgnoreCase(ExpansionType.LABEL
        .toString())) {
      expansionType = ExpansionType.LABEL;
    } else {
      throw new IllegalArgumentException(
          "The property 'expansionType' must be either URI or LABEL");
    }
    
    if (bufferSizeString != null) {
      bufferSize = Integer.parseInt(bufferSizeString);
      if (bufferSize < 1) {
        throw new IllegalArgumentException(
            "The property 'bufferSize' must be a positive (smallish) integer");
      }
    }
    
    if (typeString != null) {
      List<SKOSType> types = new ArrayList<SKOSType>();
      for (String s : typeString.split(" ")) {
        SKOSType st = SKOSType.valueOf(s.toUpperCase());
        if (st != null) {
          types.add(st);
        }
      }
      type = types.toArray(new SKOSType[types.size()]);
    }
  }
  
  @Override
  public TokenStream create(TokenStream input) {
    
    if (expansionType.equals(ExpansionType.LABEL)) {
      return new SKOSLabelFilter(input, skosEngine, new StandardAnalyzer(
          luceneMatchVersion), bufferSize, type);
      
    } else {
      return new SKOSURIFilter(input, skosEngine, new StandardAnalyzer(
          luceneMatchVersion), type);
    }
    
  }
}
