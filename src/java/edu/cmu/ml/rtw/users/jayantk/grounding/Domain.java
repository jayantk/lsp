package edu.cmu.ml.rtw.users.jayantk.grounding;

import java.io.File;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.loglinear.DiscreteLogLinearFactor;
import com.jayantkrish.jklol.preprocessing.FeatureStandardizer;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.AllAssignmentIterator;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IndexedList;
import com.jayantkrish.jklol.util.IoUtils;

import edu.cmu.ml.rtw.time.utils.IoUtil;
import edu.cmu.ml.rtw.users.jayantk.semparse.RelationType;

/**
 * A set of entities, with features for both the entities and relations between
 * them.
 * 
 * @author jayantk
 */
public class Domain implements Serializable {
  static final long serialVersionUID = 10275539478837410L;

  // A unique identifier for this domain.
  private final String domainName;

  private final VariableNumMap groundingVar1;
  private final VariableNumMap groundingVar2;
  private final VariableNumMap booleanVar;

  private final GroundingFamily categoryFamily;
  private final GroundingFamily relationFamily;

  // Predicates whose groundings are known a priori. These predicates can be
  // instantiated from a knowledge base (for example).
  private final IndexedList<String> knownPredicates;
  private final List<ParallelFactors> knownPredicateGroundings;

  private final List<GroundingExample> trainingExamples;
  private final List<GroundingExample> testExamples;
  
  // Annotated world, (optional).
  private final World actualWorld;

  private final ParallelFactors andTruthTable;

  public static final String CATEGORY_FEATURE_FILE = "osm_kb.domain.entities";
  public static final String RELATION_FEATURE_FILE = "osm_kb.domain.relations";

  private static final String TEST_DATA_FILE = "test.txt";
  // Contains (values of) category and relation predicates whose values are known beforehand.
  private static final String KB_CATEGORY_FILE = "kb.txt.categories";
  private static final String KB_RELATION_FILE = "kb.txt.relations";

  private static final double FIXED_PREDICATE_WEIGHT = 1000.0;

  public static final String KB_IGNORE_PREFIX = "kb-ignore";
  public static final String KB_IGNORE_EQUAL_PREFIX = "kb-ignore-equal";
  public static final String KB_IGNORE_ALL_PREFIX = "kb-ignore-all";

  public static final DiscreteVariable BOOLEAN_VARIABLE_TYPE = new DiscreteVariable("boolean", Arrays.asList("F", "T")); 

  public Domain(String domainName, VariableNumMap groundingVar1,
      VariableNumMap groundingVar2, VariableNumMap booleanVar,
      GroundingFamily categoryFamily, GroundingFamily relationFamily,
      IndexedList<String> knownPredicates, List<ParallelFactors> knownPredicateGroundings,
      List<GroundingExample> trainingExamples, List<GroundingExample> testExamples,
      ParallelFactors andTruthTable, World actualWorld) {
    this.domainName = Preconditions.checkNotNull(domainName);

    this.groundingVar1 = Preconditions.checkNotNull(groundingVar1);
    this.groundingVar2 = Preconditions.checkNotNull(groundingVar2);
    this.booleanVar = Preconditions.checkNotNull(booleanVar);

    this.categoryFamily = Preconditions.checkNotNull(categoryFamily);
    this.relationFamily = Preconditions.checkNotNull(relationFamily);

    this.knownPredicates = Preconditions.checkNotNull(knownPredicates);
    this.knownPredicateGroundings = Preconditions.checkNotNull(knownPredicateGroundings);

    this.trainingExamples = ImmutableList.copyOf(trainingExamples);
    this.testExamples = ImmutableList.copyOf(testExamples);

    this.andTruthTable = Preconditions.checkNotNull(andTruthTable);
    this.actualWorld = actualWorld;
  }

  public DiscreteVariable getCategoryFeatureVariable() {
    return this.categoryFamily.getFeatureVariable();
  }

  public DiscreteVariable getRelationFeatureVariable() {
    return this.relationFamily.getFeatureVariable();
  }

  public VariableNumMap getGroundingVariable1() {
    return groundingVar1;
  }
  
  public VariableNumMap getGroundingVariable2() { 
    return groundingVar2;
  }

  public VariableNumMap getBooleanVariable() {
    return booleanVar;
  }

  public String getName() {
    return domainName;
  }

  public List<GroundingExample> getTrainingExamples() {
    return trainingExamples;
  }

  public List<GroundingExample> getTestExamples() {
    return testExamples;
  }

  public List<String> getKnownRelationNames() {
    return knownPredicates.items();
  }
  
  public World getActualWorld() {
    return actualWorld;
  }

  /**
   * Gets features from this domain suitable for learning a classifier for
   * relation. These features either describe entities or pairs of entities. Use
   * this method for relations whose groundings must be learned during training.
   * 
   * @param relation
   * @return
   */
  public GroundingFamily getFamilyForRelation(RelationType relation) {
    int argCount = relation.getArgumentTypes().size();
    Preconditions.checkArgument(argCount >= 1 && argCount <= 2);
    if (argCount == 1) {
      return categoryFamily;
    } else {
      // argCount == 2
      return relationFamily;
    }
  }

  public GroundingFamily getCategoryFamily() {
    return categoryFamily;
  }

  /**
   * Gets the grounding within this domain of {@code relation}, which must be a
   * relation whose groundings are known a priori. Use this for relations whose
   * values are known beforehand (e.g., by obtaining them from a knowledge
   * base).
   * 
   * @param relation
   * @return
   */
  public ParallelFactors getGroundingForFixedRelation(RelationType relation) {
    return getGroundingForFixedRelation(relation.getName());
  }

  public ParallelFactors getGroundingForFixedRelation(String relationName) {
    String name = relationName.split("#")[0];

    Preconditions.checkArgument(knownPredicates.contains(name), "Domain %s does not contain known predicate: %s", domainName, relationName);

    return knownPredicateGroundings.get(knownPredicates.getIndex(name));
  }

  public ParallelFactors getAndTruthTable() {
    return andTruthTable;
  }

  /**
   * Expected file formats:
   * 
   * categoryFeatureFile:
   * 
   * (grounding name),(T or F),(feature name),(value)
   * 
   * relationFeatureFile:
   * 
   * (grounding name),(grounding name),(T or F),(feature name),(value)
   */
  public static Domain readDomainFromFile(String domainName, Iterable<String> categoryFeatureIterable,
      Iterable<String> relationFeatureIterable, String trainingDataFile, String testDataFile,
      String kbCategoryFile, String kbRelationFile, String goldKbFile, DiscreteVariable categoryFeatureVariableType, 
      DiscreteVariable relationFeatureVariableType, DiscreteVariable booleanVariableType, ParallelFactors andTruthTable, 
      boolean ignoreInvalidLines, boolean useGenerativeModel, boolean rescaleObjective, FeatureStandardizer 
      categoryStandardizer, FeatureStandardizer relationStandardizer) {

    // groundings are specific to each domain.
    List<String> groundingNames = Lists.newArrayList(IoUtils.readColumnFromDelimitedLines(categoryFeatureIterable, 0, ","));
    DiscreteVariable groundingVariableType = new DiscreteVariable("grounding", groundingNames);

    VariableNumMap groundingVar1, groundingVar2, booleanVar, catFeatureVar, relFeatureVar;
    groundingVar1 = VariableNumMap.singleton(0, "grounding0", groundingVariableType);
    groundingVar2 = VariableNumMap.singleton(1, "grounding1", groundingVariableType);
    booleanVar = VariableNumMap.singleton(10, "truthVal", booleanVariableType);
    catFeatureVar = VariableNumMap.singleton(100, "catFeatures", categoryFeatureVariableType);
    relFeatureVar = VariableNumMap.singleton(101, "relFeatures", relationFeatureVariableType);

    // Read in grounding features for categories.
    DiscreteFactor categoryFeatures = TableFactor.fromDelimitedFile(
        Arrays.asList(groundingVar1, booleanVar, catFeatureVar),
        categoryFeatureIterable, ",", ignoreInvalidLines);
    if (categoryStandardizer != null) {
      categoryFeatures = categoryStandardizer.apply(categoryFeatures);
    }
    GroundingFamily categoryFamily = getGroundingFamily(categoryFeatures, groundingVar1,
        booleanVar, catFeatureVar, useGenerativeModel, rescaleObjective);

    // Read in grounding features for relations.
    DiscreteFactor relationFeatures = TableFactor.fromDelimitedFile(
        Arrays.asList(groundingVar1, groundingVar2, booleanVar, relFeatureVar),
        relationFeatureIterable, ",", ignoreInvalidLines);
    if (relationStandardizer != null) {
      relationFeatures = relationStandardizer.apply(relationFeatures);
    }
    GroundingFamily relationFamily = getGroundingFamily(relationFeatures, groundingVar1.union(groundingVar2),
        booleanVar, relFeatureVar, useGenerativeModel, rescaleObjective);

    // Read in training and test data for the domain.
    List<GroundingExample> trainingData = Lists.newArrayList();
    if (trainingDataFile != null) {
      trainingData.addAll(GroundingModelUtilities
          .readTrainingData(domainName, trainingDataFile, groundingVar1, groundingVar2, booleanVar));
    }
    List<GroundingExample> testData = Lists.newArrayList();
    if (testDataFile != null) {
      testData.addAll(GroundingModelUtilities
          .readTrainingData(domainName, testDataFile, groundingVar1, groundingVar2, booleanVar));
    }

    IndexedList<String> knownRelations = IndexedList.create();
    List<ParallelFactors> knownRelationGroundings = Lists.newArrayList();

    // create a known category called kb-ignore
    TableFactor known_tf = TableFactor.unity(groundingVar1.union(booleanVar));
    TableFactor btf = TableFactor.pointDistribution(booleanVar, booleanVar.outcomeArrayToAssignment("T"));
    DiscreteFactor tfProduct = known_tf.product(btf).product(FIXED_PREDICATE_WEIGHT);
    ParallelFactors pf = new ParallelFactors(tfProduct.getWeights(), groundingVar1, booleanVar);
    knownRelations.add(KB_IGNORE_PREFIX);
    knownRelationGroundings.add(pf);

    // create a known category called kb-ignore-all
    known_tf = TableFactor.unity(VariableNumMap.unionAll(groundingVar1, groundingVar2, booleanVar));
    btf = TableFactor.pointDistribution(booleanVar, booleanVar.outcomeArrayToAssignment("T"));
    tfProduct = known_tf.product(btf).product(FIXED_PREDICATE_WEIGHT);
    pf = new ParallelFactors(tfProduct.getWeights(), groundingVar1.union(groundingVar2), booleanVar);
    knownRelations.add(KB_IGNORE_ALL_PREFIX);
    knownRelationGroundings.add(pf);

    // create a known relation called kb-ignore-equal
    TableFactorBuilder builder = new TableFactorBuilder(VariableNumMap.unionAll(groundingVar1, groundingVar2, booleanVar),
        DenseTensorBuilder.getFactory());
    Iterator<Assignment> iter = new AllAssignmentIterator(VariableNumMap.unionAll(groundingVar1, groundingVar2));
    Assignment trueAssignment = booleanVar.outcomeArrayToAssignment("T");
    Assignment falseAssignment = booleanVar.outcomeArrayToAssignment("F");
    while (iter.hasNext()) {
      Assignment a = iter.next();
      Object value1 = a.getValue(groundingVar1.getOnlyVariableNum());
      Object value2 = a.getValue(groundingVar2.getOnlyVariableNum());
      double prod = -0.5;
      if (value1.equals(value2)) {
        prod = 0.5;
      }
      builder.setWeight(a.union(trueAssignment), prod);
      builder.setWeight(a.union(falseAssignment), -1.0 * prod);
    }
    Tensor weights = builder.build().getWeights().elementwiseProduct(FIXED_PREDICATE_WEIGHT);
    pf = new ParallelFactors(DenseTensor.copyOf(weights), groundingVar1.union(groundingVar2), booleanVar);
    knownRelations.add(KB_IGNORE_EQUAL_PREFIX);
    knownRelationGroundings.add(pf);

    //load a fixed category and relations from a file
    if (kbCategoryFile != null) {      
      Set<String> knownRelationNamesSet = Sets.newHashSet(IoUtil.LoadFieldFromFile(kbCategoryFile, ",", 0));
      DiscreteVariable knownRelationNames = new DiscreteVariable("knownRelations", knownRelationNamesSet);
      VariableNumMap knownRelationVar = VariableNumMap.singleton(9, "knownRelation", knownRelationNames);

      TableFactor knownRelationValues = TableFactor.fromDelimitedFile(
          Arrays.asList(knownRelationVar, groundingVar1, booleanVar),
          IoUtil.LoadFile(kbCategoryFile), ",", false);

      for (String knownRelationName : knownRelationNamesSet) {
        knownRelations.add(knownRelationName);

        DiscreteFactor factor = knownRelationValues.conditional(knownRelationVar.outcomeArrayToAssignment(knownRelationName));

        DiscreteFactor trueFactor = TableFactor.unity(factor.getVars()).product(TableFactor.pointDistribution(
            booleanVar, booleanVar.outcomeArrayToAssignment("T")));
        DiscreteFactor falseFactor = TableFactor.unity(factor.getVars()).product(TableFactor.pointDistribution(
            booleanVar, booleanVar.outcomeArrayToAssignment("F")));

        // Find all variables whose values were specified in the file.
        factor = factor.marginalize(booleanVar.getVariableNums()).outerProduct(TableFactor.unity(booleanVar));
        // Map those given values to true weight 1, false weight -1.
        Tensor tensor = factor.product(falseFactor.product(-1.0).add(trueFactor)).getWeights();

        // Map t: 0 f: 0 to t: -1/2, f: 1/2, while simultaneously mapping 
        // t: 1, f: -1 to t: 1/2 f: -1/2.
        tensor = tensor.elementwiseAddition(trueFactor.getWeights().elementwiseProduct(-0.5));
        tensor = tensor.elementwiseAddition(falseFactor.getWeights().elementwiseProduct(0.5));
        tensor = tensor.elementwiseProduct(FIXED_PREDICATE_WEIGHT);

        knownRelationGroundings.add(new ParallelFactors(tensor, groundingVar1, booleanVar));
      }
    }

    if (kbRelationFile != null) {
      Set<String> knownRelationNamesSet = Sets.newHashSet(IoUtil.LoadFieldFromFile(kbRelationFile, ",", 0));
      DiscreteVariable knownRelationNames = new DiscreteVariable("knownRelations", knownRelationNamesSet);
      VariableNumMap knownRelationVar = VariableNumMap.singleton(9, "knownRelation", knownRelationNames);

      TableFactor knownRelationValues = TableFactor.fromDelimitedFile(
          Arrays.asList(knownRelationVar, groundingVar1, groundingVar2, booleanVar),
          IoUtil.LoadFile(kbRelationFile), ",", false);

      for (String knownRelationName : knownRelationNamesSet) {
        knownRelations.add(knownRelationName);

        DiscreteFactor factor = knownRelationValues.conditional(knownRelationVar.outcomeArrayToAssignment(knownRelationName));

        DiscreteFactor trueFactor = TableFactor.unity(factor.getVars()).product(TableFactor.pointDistribution(
            booleanVar, booleanVar.outcomeArrayToAssignment("T")));
        DiscreteFactor falseFactor = TableFactor.unity(factor.getVars()).product(TableFactor.pointDistribution(
            booleanVar, booleanVar.outcomeArrayToAssignment("F")));


        factor = factor.marginalize(booleanVar.getVariableNums()).outerProduct(TableFactor.unity(booleanVar));
        Tensor tensor = factor.product(falseFactor.product(-1.0).add(trueFactor)).getWeights();

        tensor = tensor.elementwiseAddition(trueFactor.getWeights().elementwiseProduct(-0.5));
        tensor = tensor.elementwiseAddition(falseFactor.getWeights().elementwiseProduct(0.5));
        tensor = tensor.elementwiseProduct(FIXED_PREDICATE_WEIGHT);
        // tensor = tensor.elementwiseProduct(Double.POSITIVE_INFINITY);

        knownRelationGroundings.add(new ParallelFactors(DenseTensor.copyOf(tensor), groundingVar1.union(groundingVar2), booleanVar));
      }
    }

    World actualWorld = null;
    if (goldKbFile != null) {
      Collection<GroundingExample> goldPredicateExamples = GroundingModelUtilities.readTrainingData(
          domainName, goldKbFile, groundingVar1, groundingVar2, booleanVar);
      IndexedList<String> predicateNames = IndexedList.create();
      List<ParallelFactors> predicateGroundings = Lists.newArrayList();

      predicateNames.addAll(knownRelations);
      predicateGroundings.addAll(knownRelationGroundings);
      for (GroundingExample example : goldPredicateExamples) {
        if (example.hasObservedRelation()) {
          Tensor groundingWeightTensor = example.getGrounding().elementwiseProduct(FIXED_PREDICATE_WEIGHT);
	  Preconditions.checkState(!predicateNames.contains(example.getObservedRelationName()));
          predicateNames.add(example.getObservedRelationName());

          int numDims = groundingWeightTensor.getDimensionNumbers().length - 1;
          if (numDims == 1) {
            // Category
            predicateGroundings.add(new ParallelFactors(DenseTensor.copyOf(groundingWeightTensor),
                groundingVar1, booleanVar));
          } else {
            // Relation
            predicateGroundings.add(new ParallelFactors(DenseTensor.copyOf(groundingWeightTensor),
                groundingVar1.union(groundingVar2), booleanVar));
          }
        } 
      }
      
      actualWorld = new World(groundingVar1, groundingVar2, booleanVar, predicateNames, predicateGroundings);
    }

    return new Domain(domainName, groundingVar1, groundingVar2, booleanVar,
        categoryFamily, relationFamily, knownRelations, knownRelationGroundings,
        trainingData, testData, andTruthTable, actualWorld);
  }

  private static GroundingFamily getGroundingFamily(DiscreteFactor features, VariableNumMap variableVars,
      VariableNumMap valueVars, VariableNumMap featureVars, boolean useGenerativeModel, boolean rescaleObjective) {
    if (useGenerativeModel) {
      // The gaussian version of this family requires us to tweak the format of the
      // feature vectors.
      DiscreteFactor reformattedFeatures = features.maxMarginalize(valueVars.getVariableNums())
          .outerProduct(TableFactor.unity(valueVars));
      return new GaussianGroundingFamily(featureVars, reformattedFeatures, 
          variableVars, valueVars);
    } else {
      DiscreteLogLinearFactor relationFeatureFactor = new DiscreteLogLinearFactor(
          variableVars.union(valueVars), featureVars, features);
      return new RelationGroundingFamily(relationFeatureFactor,
          variableVars, valueVars, rescaleObjective);
    }
  }

  public static Domain readDomainFromDirectory(String domainDirectory, GroundingModel model, 
      boolean useGenerativeModel, boolean rescaleObjective, FeatureStandardizer categoryStandardizer,
      FeatureStandardizer relationStandardizer) {
    Preconditions.checkArgument(model != null);

    String dirName = domainDirectory + "/";
    String domainName = domainDirectory;  
    String categoryFeatureFile = dirName + CATEGORY_FEATURE_FILE;
    String relationFeatureFile = dirName + RELATION_FEATURE_FILE;

    String trainingDataFile = checkFileExists(dirName + "training.txt");
    String testDataFile = checkFileExists(dirName + TEST_DATA_FILE);
    String kbCategoryFile = checkFileExists(dirName + KB_CATEGORY_FILE);
    String kbRelationFile = checkFileExists(dirName + KB_RELATION_FILE);

    DiscreteVariable categoryFeatureVariableType = model.getCategoryFeatureVariable(); 
    DiscreteVariable relationFeatureVariableType = model.getRelationFeatureVariable();

    return readDomainFromFile(domainName, IoUtil.LoadFile(categoryFeatureFile), 
        IoUtil.LoadFile(relationFeatureFile), trainingDataFile, 
        testDataFile, kbCategoryFile, kbRelationFile, null, categoryFeatureVariableType, 
        relationFeatureVariableType, BOOLEAN_VARIABLE_TYPE, buildAndTruthTable(), true, useGenerativeModel, rescaleObjective,
        categoryStandardizer, relationStandardizer);
  }

  public static Domain constructDomainFromIterables(String domainName, Iterable<String> categoryFeatures, 
      Iterable<String> relationFeatures, DiscreteVariable categoryFeatureVariable, 
      DiscreteVariable relationFeatureVariable, boolean useGenerativeModel,
      boolean rescaleObjective,
      FeatureStandardizer categoryStandardizer, FeatureStandardizer relationStandardizer) {
    return readDomainFromFile(domainName, categoryFeatures, relationFeatures, null, 
        null, null, null, null, categoryFeatureVariable, relationFeatureVariable, BOOLEAN_VARIABLE_TYPE, 
        buildAndTruthTable(), true, useGenerativeModel, rescaleObjective, categoryStandardizer, relationStandardizer);
  }

  public static List<Domain> readDomainsFromDirectory(String domainDirectory, String trainingFilename, String goldKbFilename,
      int maxTrainingExamples, boolean useGenerativeModel, boolean rescaleFeatures, boolean standardizeFeatures) {

    return readDomainsFromDirectory(domainDirectory, trainingFilename, goldKbFilename,
        maxTrainingExamples, null, useGenerativeModel, rescaleFeatures, standardizeFeatures);
  }

  private static String checkFileExists(String filename) {
    File file = new File(filename);
    if (file.exists()) {
      return filename;
    } else {
      return null;
    }
  }

  public static List<Domain> readDomainsFromDirectory(String domainDirectory, String trainingFileName, 
      String goldKbFile, int maxTrainingExamples, GroundingModel oldModel,
      boolean useGenerativeModel, boolean rescaleObjective, boolean standardizeFeatures) {
    List<String> categoryFeatureFiles = Lists.newArrayList();
    List<String> relationFeatureFiles = Lists.newArrayList();
    List<String> trainingDataFiles = Lists.newArrayList();
    List<String> testDataFiles = Lists.newArrayList();
    List<String> goldKbFiles = Lists.newArrayList();
    List<String> kbCategoryFiles = Lists.newArrayList();
    List<String> kbRelationFiles = Lists.newArrayList();
    List<String> domainNames = Lists.newArrayList();

    File dir = new File(domainDirectory);
    File listDir[] = dir.listFiles();
    for (int i = 0; i < listDir.length; i++) {
      //only iterate through x training examples
      if(i > maxTrainingExamples)
        break;

      //load all the relevent directories 
      if (listDir[i].isDirectory() && !listDir[i].isHidden()) {
        String dirName = listDir[i].getPath() + "/";
        System.out.println(dirName);

        // Skip domains that don't have a category feature file.
        File file = new File(dirName + CATEGORY_FEATURE_FILE);
        if (!file.exists()) {
          System.out.println("skipping " + dirName + " no features.");
          continue;
        }

        categoryFeatureFiles.add(dirName + CATEGORY_FEATURE_FILE);
        relationFeatureFiles.add(dirName + RELATION_FEATURE_FILE);
        trainingDataFiles.add(dirName + trainingFileName);
        testDataFiles.add(checkFileExists(dirName + TEST_DATA_FILE));

        file = new File(dirName + KB_CATEGORY_FILE);
        if (file.exists()) {
          kbCategoryFiles.add(dirName + KB_CATEGORY_FILE);
        } else {
          kbCategoryFiles.add(null);
        }

        file = new File(dirName + KB_RELATION_FILE);
        if (file.exists()) {
          kbRelationFiles.add(dirName + KB_RELATION_FILE);
        } else {
          kbRelationFiles.add(null);
        }

        if (goldKbFile != null) {
          file = new File(dirName + goldKbFile);
          if (file.exists()) {
            goldKbFiles.add(dirName + goldKbFile);
          } else {
            goldKbFiles.add(null);
          }
        } else {
          goldKbFiles.add(null);
        }

        domainNames.add(listDir[i].getName());
      }
    }
    DiscreteVariable categoryFeatureVariableType, relationFeatureVariableType;
    if(oldModel==null){
      Set<String> categoryFeatureNames = Sets.newHashSet();
      Set<String> relationFeatureNames = Sets.newHashSet();
      // Index all category and relation features to ensure conformity in their
      // numberings across all domains.
      for (String categoryFeatureFile : categoryFeatureFiles) {
        categoryFeatureNames.addAll(IoUtil.LoadFieldFromFile(categoryFeatureFile, ",", 2));
      }

      System.out.println("files"+relationFeatureFiles);
      for (String relationFeatureFile : relationFeatureFiles) { 
        relationFeatureNames.addAll(IoUtil.LoadFieldFromFile(relationFeatureFile, ",", 3));
      }

      if (relationFeatureNames.size() == 0) {
        // There must be at least one feature for the code to work.
        // Note that adding an extra feature won't change program behavior.
        relationFeatureNames.add("not_a_feature");
      }

      if (useGenerativeModel) {
        categoryFeatureNames.remove("bias");
        relationFeatureNames.remove("bias");
      }

      categoryFeatureVariableType = new DiscreteVariable("category_features", categoryFeatureNames);
      relationFeatureVariableType = new DiscreteVariable("relation_features", relationFeatureNames);
    } else {
      categoryFeatureVariableType = oldModel.getCategoryFeatureVariable(); 
      relationFeatureVariableType = oldModel.getRelationFeatureVariable();
    }

    boolean ignoreInvalidLines = oldModel != null || useGenerativeModel;
    FeatureStandardizer categoryStandardizer = null;
    FeatureStandardizer relationStandardizer = null;
    if (standardizeFeatures) {
      List<DiscreteFactor> categoryFactors = Lists.newArrayList();
      List<DiscreteFactor> relationFactors = Lists.newArrayList();

      for (int i = 0; i < categoryFeatureFiles.size(); i++) {
        String categoryFeatureFile = categoryFeatureFiles.get(i);
        String relationFeatureFile = relationFeatureFiles.get(i);
        List<String> groundingNames = Lists.newArrayList(IoUtils.readColumnFromDelimitedFile(categoryFeatureFile, 0, ","));
        DiscreteVariable groundingVariableType = new DiscreteVariable("grounding", groundingNames);

        VariableNumMap groundingVar1 = VariableNumMap.singleton(0, "grounding0", groundingVariableType);
        VariableNumMap groundingVar2 = VariableNumMap.singleton(1, "grounding1", groundingVariableType);
        VariableNumMap booleanVar = VariableNumMap.singleton(10, "truthVal", BOOLEAN_VARIABLE_TYPE);
        VariableNumMap catFeatureVar = VariableNumMap.singleton(100, "catFeatures", categoryFeatureVariableType);
        VariableNumMap relFeatureVar = VariableNumMap.singleton(101, "relFeatures", relationFeatureVariableType);

        // Read in grounding features for categories.
        DiscreteFactor categoryFeatures = TableFactor.fromDelimitedFile(
            Arrays.asList(groundingVar1, booleanVar, catFeatureVar),
            IoUtils.readLines(categoryFeatureFile), ",", ignoreInvalidLines);
        categoryFactors.add(categoryFeatures.maxMarginalize(booleanVar.getVariableNums()));

        DiscreteFactor relationFeatures = TableFactor.fromDelimitedFile(
            Arrays.asList(groundingVar1, groundingVar2, booleanVar, relFeatureVar),
            IoUtils.readLines(relationFeatureFile), ",", ignoreInvalidLines);
        relationFactors.add(relationFeatures.maxMarginalize(booleanVar.getVariableNums()));
      }

      categoryStandardizer = FeatureStandardizer.estimateFrom(categoryFactors, 100, null, 1.0 / Math.sqrt(categoryFeatureVariableType.numValues()));
      relationStandardizer = FeatureStandardizer.estimateFrom(relationFactors, 101, null, 1.0 / Math.sqrt(relationFeatureVariableType.numValues()));
    }

    List<Domain> domains = Lists.newArrayList();
    for (int i = 0; i < categoryFeatureFiles.size(); i++) {
      domains.add(readDomainFromFile(domainNames.get(i), IoUtil.LoadFile(categoryFeatureFiles.get(i)), 
          IoUtil.LoadFile(relationFeatureFiles.get(i)),
          trainingDataFiles.get(i), testDataFiles.get(i), kbCategoryFiles.get(i), kbRelationFiles.get(i),
          goldKbFiles.get(i), categoryFeatureVariableType, relationFeatureVariableType, BOOLEAN_VARIABLE_TYPE, 
          buildAndTruthTable(), ignoreInvalidLines, useGenerativeModel, rescaleObjective, categoryStandardizer, relationStandardizer));
    }

    return domains;
  }

  public static final ParallelFactors buildAndTruthTable() {
    // Build a truth table for conjunctions in each domain.
    SparseTensorBuilder truthTableBuilder = new SparseTensorBuilder(new int[] { 100, 101, 102 }, new int[] { 2, 2, 2 });
    truthTableBuilder.put(new int[] { 1, 1, 1 }, 1.0);
    truthTableBuilder.put(new int[] { 0, 1, 0 }, 1.0);
    truthTableBuilder.put(new int[] { 1, 0, 0 }, 1.0);
    truthTableBuilder.put(new int[] { 0, 0, 0 }, 1.0);
    VariableNumMap truthTableVars = new VariableNumMap(Ints.asList(100, 101, 102),
        Arrays.asList("input1", "input2", "output"), 
        Arrays.asList(BOOLEAN_VARIABLE_TYPE, BOOLEAN_VARIABLE_TYPE, BOOLEAN_VARIABLE_TYPE));
    return new ParallelFactors(truthTableBuilder.build(), VariableNumMap.EMPTY, truthTableVars);
  }
}