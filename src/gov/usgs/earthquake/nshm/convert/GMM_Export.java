package gov.usgs.earthquake.nshm.convert;

import static com.google.common.base.Preconditions.checkArgument;
import static org.opensha.gmm.GMM.AB_03_CASC_SLAB;
import static org.opensha.gmm.GMM.AB_03_GLOB_INTER;
import static org.opensha.gmm.GMM.AB_03_GLOB_SLAB;
import static org.opensha.gmm.GMM.AB_06_140BAR;
import static org.opensha.gmm.GMM.AB_06_140BAR_AB;
import static org.opensha.gmm.GMM.AB_06_140BAR_J;
import static org.opensha.gmm.GMM.AB_06_200BAR;
import static org.opensha.gmm.GMM.AB_06_200BAR_AB;
import static org.opensha.gmm.GMM.AB_06_200BAR_J;
import static org.opensha.gmm.GMM.AB_06_PRIME;
import static org.opensha.gmm.GMM.AM_09_INTER;
import static org.opensha.gmm.GMM.ASK_14;
import static org.opensha.gmm.GMM.ATKINSON_08_PRIME;
import static org.opensha.gmm.GMM.BA_08;
import static org.opensha.gmm.GMM.BCHYDRO_12_INTER;
import static org.opensha.gmm.GMM.BCHYDRO_12_SLAB;
import static org.opensha.gmm.GMM.BSSA_14;
import static org.opensha.gmm.GMM.CAMPBELL_03;
import static org.opensha.gmm.GMM.CAMPBELL_03_AB;
import static org.opensha.gmm.GMM.CAMPBELL_03_J;
import static org.opensha.gmm.GMM.CB_08;
import static org.opensha.gmm.GMM.CB_14;
import static org.opensha.gmm.GMM.CY_08;
import static org.opensha.gmm.GMM.CY_14;
import static org.opensha.gmm.GMM.FRANKEL_96;
import static org.opensha.gmm.GMM.FRANKEL_96_AB;
import static org.opensha.gmm.GMM.FRANKEL_96_J;
import static org.opensha.gmm.GMM.IDRISS_14;
import static org.opensha.gmm.GMM.PEZESHK_11;
import static org.opensha.gmm.GMM.SILVA_02;
import static org.opensha.gmm.GMM.SILVA_02_AB;
import static org.opensha.gmm.GMM.SILVA_02_J;
import static org.opensha.gmm.GMM.SOMERVILLE_01;
import static org.opensha.gmm.GMM.TORO_97_MB;
import static org.opensha.gmm.GMM.TORO_97_MW;
import static org.opensha.gmm.GMM.TP_05;
import static org.opensha.gmm.GMM.TP_05_AB;
import static org.opensha.gmm.GMM.TP_05_J;
import static org.opensha.gmm.GMM.YOUNGS_97_INTER;
import static org.opensha.gmm.GMM.YOUNGS_97_SLAB;
import static org.opensha.gmm.GMM.ZHAO_06_INTER;
import static org.opensha.gmm.GMM.ZHAO_06_SLAB;
import static org.opensha.gmm.GMM_Attribute.ID;
import static org.opensha.gmm.GMM_Attribute.WEIGHT;
import static org.opensha.gmm.GMM_Attribute.MAX_DISTANCE;
import static org.opensha.gmm.GMM_Attribute.VALUES;
import static org.opensha.gmm.GMM_Attribute.WEIGHTS;
import static org.opensha.gmm.GMM_Element.GROUND_MOTION_MODELS;
import static org.opensha.gmm.GMM_Element.MODEL_SET;
import static org.opensha.gmm.GMM_Element.MODEL;
import static org.opensha.gmm.GMM_Element.UNCERTAINTY;
import static org.opensha.util.Parsing.addAttribute;
import static org.opensha.util.Parsing.addElement;
import gov.usgs.earthquake.nshm.util.SourceRegion;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.opensha.eq.forecast.SourceType;
import org.opensha.gmm.GMM;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.common.base.StandardSystemProperty;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
public class GMM_Export {

	// distance beyond which gmmMapAlt will be used in claculations
	private static final double WUS_R_CUT_08 = 200.0;
	private static final double WUS_R_CUT_14 = 300.0;
	private static final double CEUS_R_CUT_08 = 1000.0;
	private static final double CEUS_R_CUT_14_A = 500.0;
	private static final double CEUS_R_CUT_14_B = 1000.0;
	private static final double INTERFACE_R_CUT = 1000.0;
	
	// need to break out CEUS mb converted grids as their GMMs would effectively
	// override the CEUS grid GMM's in the t08 table below
	private Map<GMM, Double> ceusGridMap08;
	private Map<GMM, Double> ceusGridMap14;
	private Map<GMM, Double> ceusGridMap08_J;
	private Map<GMM, Double> ceusGridMap08_AB;
	private Map<GMM, Double> ceusMap14_rCut;
	private Map<GMM, Double> ceusFaultMap08;
	private Map<GMM, Double> ceusFaultMap14;

	private Map<GMM, Double> wusGridMap08;
	private Map<GMM, Double> wusGridMap14;
	private Map<GMM, Double> wusFaultMap08;
	private Map<GMM, Double> wusFaultMap14;
	private Map<GMM, Double> wusInterfaceMap08;
	private Map<GMM, Double> wusInterfaceMap14;
	private Map<GMM, Double> wusSlabMap08;
	private Map<GMM, Double> wusSlabMap14;

	// additional epistemic uncertainty on ground motion
	private static final double[] WUS_UNC_WTS = { 0.185, 0.630, 0.185 };
	private static final double[] WUS_UNC_08 = { 0.375, 0.230, 0.400, 0.210, 0.225, 0.360, 0.245, 0.230, 0.310 };
	private static final double[] WUS_UNC_14 = { 0.375, 0.250, 0.400, 0.220, 0.230, 0.360, 0.220, 0.230, 0.330 };

	private static final String S = StandardSystemProperty.FILE_SEPARATOR.value();
	private static final String MODEL_PATH = "forecasts" + S;
	private static final String GMM_FILE = "gmm.xml";
	private static final String CEUS_DIR = SourceRegion.CEUS.toString();
	private static final String WUS_DIR = SourceRegion.WUS.toString();
	
	public static void main(String[] args) throws Exception {
		GMM_Export exporter = new GMM_Export();
		exporter.createCEUS_2008();
		exporter.createWUS_2008();
	}
	
	GMM_Export() {
		initMaps();
	}
	
	@SuppressWarnings({"incomplete-switch", "unchecked"})
	private void createCEUS_2008() throws Exception {
		String modelPath = MODEL_PATH + "2008" + S + CEUS_DIR + S;
		for (SourceType type : SourceType.values()) {
			File typeDir = new File(modelPath + S + type.toString());
			if (!typeDir.exists()) continue;
			File dest = new File(typeDir, GMM_FILE);
			switch (type) {
				case CLUSTER:
					writeFile(dest, 
						Lists.newArrayList(ceusFaultMap08),
						Lists.newArrayList(CEUS_R_CUT_08),
						null, null);
					break;
				case FAULT:
					writeFile(dest,
						Lists.newArrayList(ceusFaultMap08),
						Lists.newArrayList(CEUS_R_CUT_08),
						null, null);
					break;
				case GRID:
					writeFile(dest,
						Lists.newArrayList(ceusGridMap08),
						Lists.newArrayList(CEUS_R_CUT_08),
						null, null);
					File mb_ab = new File(typeDir + S + "mb-AtkinBoore" + S + GMM_FILE);
					writeFile(mb_ab,
						Lists.newArrayList(ceusGridMap08_AB),
						Lists.newArrayList(CEUS_R_CUT_08),
						null, null);
					File mb_j = new File(typeDir + S + "mb-Johnson" + S + GMM_FILE);
					writeFile(mb_j,
						Lists.newArrayList(ceusGridMap08_J),
						Lists.newArrayList(CEUS_R_CUT_08),
						null, null);
					break;
			}
		}
	}
	
	@SuppressWarnings({"incomplete-switch", "unchecked"})
	private void createCEUS_2014() throws Exception {
		String modelPath = MODEL_PATH + "2014" + S + CEUS_DIR + S;
		for (SourceType type : SourceType.values()) {
			File typeDir = new File(modelPath + S + type.toString());
			if (!typeDir.exists()) continue;
			File dest = new File(typeDir, GMM_FILE);
			switch (type) {
				case CLUSTER:
					writeFile(dest,
						Lists.newArrayList(ceusFaultMap14, ceusMap14_rCut),
						Lists.newArrayList(CEUS_R_CUT_14_A, CEUS_R_CUT_14_B),
						null, null);
					break;
				case FAULT:
					writeFile(dest,
						Lists.newArrayList(ceusFaultMap14, ceusMap14_rCut),
						Lists.newArrayList(CEUS_R_CUT_14_A, CEUS_R_CUT_14_B),
						null, null);
					break;
				case GRID:
					writeFile(dest,
						Lists.newArrayList(ceusGridMap14, ceusMap14_rCut),
						Lists.newArrayList(CEUS_R_CUT_14_A, CEUS_R_CUT_14_B),
						null, null);
					break;
			}
		}
	}

	@SuppressWarnings({"incomplete-switch", "unchecked"})
	private void createWUS_2008() throws Exception {
		String modelPath = MODEL_PATH + "2008" + S + WUS_DIR + S;
		for (SourceType type : SourceType.values()) {
			File typeDir = new File(modelPath + S + type.toString());
			if (!typeDir.exists()) continue;
			File dest = new File(typeDir, GMM_FILE);
			switch (type) {
				case FAULT:
					writeFile(dest,
						Lists.newArrayList(wusFaultMap08),
						Lists.newArrayList(WUS_R_CUT_08),
						WUS_UNC_08, WUS_UNC_WTS);
					break;
				case GRID:
					writeFile(dest,
						Lists.newArrayList(wusGridMap08),
						Lists.newArrayList(WUS_R_CUT_08),
						WUS_UNC_08, WUS_UNC_WTS);
					break;
				case INTERFACE:
					writeFile(dest,
						Lists.newArrayList(wusInterfaceMap08),
						Lists.newArrayList(INTERFACE_R_CUT),
						null, null);
					break;
				case SLAB:
					writeFile(dest,
						Lists.newArrayList(wusSlabMap08),
						Lists.newArrayList(WUS_R_CUT_08),
						null, null);
					break;
			}
		}
	}
	
	@SuppressWarnings({"incomplete-switch", "unchecked"})
	private void createWUS_2014() throws Exception {
		String modelPath = MODEL_PATH + "2014" + S + WUS_DIR + S;
		for (SourceType type : SourceType.values()) {
			File typeDir = new File(modelPath + S + type.toString());
			if (!typeDir.exists()) continue;
			File dest = new File(typeDir, GMM_FILE);
			switch (type) {
				case CLUSTER:
					writeFile(dest,
						Lists.newArrayList(wusFaultMap14),
						Lists.newArrayList(WUS_R_CUT_14),
						null, null);
					break;
				case FAULT:
					writeFile(dest,
						Lists.newArrayList(wusFaultMap14),
						Lists.newArrayList(WUS_R_CUT_14),
						null, null);
					break;
				case GRID:
					writeFile(dest,
						Lists.newArrayList(wusGridMap14),
						Lists.newArrayList(WUS_R_CUT_14),
						null, null);
					break;
				case INTERFACE:
					writeFile(dest,
						Lists.newArrayList(wusInterfaceMap14),
						Lists.newArrayList(INTERFACE_R_CUT),
						null, null);
					break;
				case SLAB:
					writeFile(dest,
						Lists.newArrayList(wusSlabMap14),
						Lists.newArrayList(WUS_R_CUT_14),
						null, null);
					break;
			}
		}
	}


	
	private void writeFile(File dest, List<Map<GMM, Double>> gmmMapList, List<Double> cutoffList,
			double[] uncValues, double[] uncWeights) throws ParserConfigurationException,
			TransformerException {

		// uncValues and uncWeights may be null
		
		checkArgument(gmmMapList.size() == cutoffList.size());
		
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

		// root element
		Document doc = docBuilder.newDocument();
		doc.setXmlStandalone(true);
		Element root = doc.createElement(GROUND_MOTION_MODELS.toString());
		doc.appendChild(root);
		
		if (uncValues != null) {
			Element unc = addElement(UNCERTAINTY, root);
			addAttribute(VALUES, uncValues, unc);
			addAttribute(WEIGHTS, uncWeights, unc);
		}

		int count = 0;
		for (Map<GMM, Double> gmmMap : gmmMapList) {
			Element gmmSetElem = addElement(MODEL_SET, root);
			addAttribute(MAX_DISTANCE, cutoffList.get(count++), gmmSetElem);
			for (Entry<GMM, Double> entry : gmmMap.entrySet()) {
				Element gmmElem = addElement(MODEL, gmmSetElem);
				addAttribute(ID, entry.getKey().name(), gmmElem);
				addAttribute(WEIGHT, entry.getValue(), gmmElem);
			}
		}

		// write the content to xml file
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer trans = transformerFactory.newTransformer();
		trans.setOutputProperty(OutputKeys.INDENT, "yes");
		trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		trans.setOutputProperty(OutputKeys.STANDALONE, "yes");
		DOMSource source = new DOMSource(doc);
		StreamResult result = new StreamResult(dest);
		trans.transform(source, result);
	}
	
	private void initMaps() {
		
		wusFaultMap08 = Maps.newEnumMap(GMM.class);
		wusFaultMap08.put(BA_08,     0.3333);
		wusFaultMap08.put(CB_08,     0.3333);
		wusFaultMap08.put(CY_08,     0.3334);

		wusFaultMap14 = Maps.newEnumMap(GMM.class);
		wusFaultMap14.put(ASK_14,    0.22);
		wusFaultMap14.put(BSSA_14,   0.22);
		wusFaultMap14.put(CB_14,     0.22);
		wusFaultMap14.put(CY_14,     0.22);
		wusFaultMap14.put(IDRISS_14, 0.12);
		
		wusGridMap08 = wusFaultMap08;
		wusGridMap14 = wusFaultMap14;
		
		
		wusInterfaceMap08 = Maps.newEnumMap(GMM.class);
		wusInterfaceMap08.put(AB_03_GLOB_INTER,  0.25);
		wusInterfaceMap08.put(YOUNGS_97_INTER,   0.25);
		wusInterfaceMap08.put(ZHAO_06_INTER,     0.50);

		wusInterfaceMap14 = Maps.newEnumMap(GMM.class);
		wusInterfaceMap14.put(AB_03_GLOB_INTER,  0.10);
		wusInterfaceMap14.put(AM_09_INTER,       0.30);
		wusInterfaceMap14.put(BCHYDRO_12_INTER,  0.30);
		wusInterfaceMap14.put(ZHAO_06_INTER,     0.30);
		
		
		wusSlabMap08 = Maps.newEnumMap(GMM.class);
		wusSlabMap08.put(AB_03_CASC_SLAB,  0.25);
		wusSlabMap08.put(AB_03_GLOB_SLAB,  0.25);
		wusSlabMap08.put(YOUNGS_97_SLAB,   0.50);
		
		wusSlabMap14 = Maps.newEnumMap(GMM.class);
		wusSlabMap14.put(AB_03_CASC_SLAB,  0.1665);
		wusSlabMap14.put(AB_03_GLOB_SLAB,  0.1665);
		wusSlabMap14.put(BCHYDRO_12_SLAB,  0.3330);
		wusSlabMap14.put(ZHAO_06_SLAB,     0.3340);
		
		
		
		ceusFaultMap08 = Maps.newEnumMap(GMM.class);
		ceusFaultMap08.put(AB_06_140BAR,      0.1);
		ceusFaultMap08.put(AB_06_200BAR,      0.1);
		ceusFaultMap08.put(CAMPBELL_03,       0.1);
		ceusFaultMap08.put(FRANKEL_96,        0.1);
		ceusFaultMap08.put(SILVA_02,          0.1);
		ceusFaultMap08.put(SOMERVILLE_01,     0.2);
		ceusFaultMap08.put(TORO_97_MW,        0.2);
		ceusFaultMap08.put(TP_05,             0.1);
		

		ceusFaultMap14 = Maps.newEnumMap(GMM.class);
		ceusFaultMap14.put(AB_06_PRIME,       0.22);
		ceusFaultMap14.put(ATKINSON_08_PRIME, 0.08);
		ceusFaultMap14.put(CAMPBELL_03,       0.11);
		ceusFaultMap14.put(FRANKEL_96,        0.06);
		ceusFaultMap14.put(SILVA_02,          0.06);
		ceusFaultMap14.put(SOMERVILLE_01,     0.10);
		ceusFaultMap14.put(PEZESHK_11,        0.15);
		ceusFaultMap14.put(TORO_97_MW,        0.11);
		ceusFaultMap14.put(TP_05,             0.11);
		
		
		// for non-mb, fixed strike sources and RLMEs
		ceusGridMap08 = ceusFaultMap08;

		// for mb conversions
		ceusGridMap08_J = Maps.newEnumMap(GMM.class);
		ceusGridMap08_J.put(AB_06_140BAR_J,       0.125);
		ceusGridMap08_J.put(AB_06_200BAR_J,       0.125);
		ceusGridMap08_J.put(CAMPBELL_03_J,        0.125);
		ceusGridMap08_J.put(FRANKEL_96_J,         0.125);
		ceusGridMap08_J.put(SILVA_02_J,           0.125);
		ceusGridMap08_J.put(TORO_97_MB,           0.250);
		ceusGridMap08_J.put(TP_05_J,              0.125);

		ceusGridMap08_AB = Maps.newEnumMap(GMM.class);
		ceusGridMap08_AB.put(AB_06_140BAR_AB,     0.125);
		ceusGridMap08_AB.put(AB_06_200BAR_AB,     0.125);
		ceusGridMap08_AB.put(CAMPBELL_03_AB,      0.125);
		ceusGridMap08_AB.put(FRANKEL_96_AB,       0.125);
		ceusGridMap08_AB.put(SILVA_02_AB,         0.125);
		ceusGridMap08_AB.put(TORO_97_MB,          0.250);
		ceusGridMap08_AB.put(TP_05_AB,            0.125);

		ceusGridMap14 = Maps.newEnumMap(GMM.class);
		ceusGridMap14.put(AB_06_PRIME,        0.25);
		ceusGridMap14.put(ATKINSON_08_PRIME,  0.08);
		ceusGridMap14.put(CAMPBELL_03,        0.13);
		ceusGridMap14.put(FRANKEL_96,         0.06);
		ceusGridMap14.put(SILVA_02,           0.06);
		ceusGridMap14.put(PEZESHK_11,         0.16);
		ceusGridMap14.put(TORO_97_MW,         0.13);
		ceusGridMap14.put(TP_05,              0.13);
		
		
		// map for fault and grid is the same beyond 500km
		ceusMap14_rCut = Maps.newEnumMap(GMM.class);
		ceusMap14_rCut.put(AB_06_PRIME,       0.30);
		ceusMap14_rCut.put(CAMPBELL_03,       0.17);
		ceusMap14_rCut.put(FRANKEL_96,        0.16);
		ceusMap14_rCut.put(PEZESHK_11,        0.20);
		ceusMap14_rCut.put(TP_05,             0.17);

	}
	
}
