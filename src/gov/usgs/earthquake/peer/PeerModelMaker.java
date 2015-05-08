package gov.usgs.earthquake.peer;

import static gov.usgs.earthquake.peer.PeerTestData.*;
import static org.opensha.eq.fault.surface.RuptureScaling.PEER;
import static org.opensha.eq.model.SourceAttribute.A;
import static org.opensha.eq.model.SourceAttribute.DEPTH;
import static org.opensha.eq.model.SourceAttribute.DIP;
import static org.opensha.eq.model.SourceAttribute.FLOATS;
import static org.opensha.eq.model.SourceAttribute.M;
import static org.opensha.eq.model.SourceAttribute.MAGS;
import static org.opensha.eq.model.SourceAttribute.MAG_DEPTH_MAP;
import static org.opensha.eq.model.SourceAttribute.NAME;
import static org.opensha.eq.model.SourceAttribute.RAKE;
import static org.opensha.eq.model.SourceAttribute.RATES;
import static org.opensha.eq.model.SourceAttribute.RUPTURE_SCALING;
import static org.opensha.eq.model.SourceAttribute.TYPE;
import static org.opensha.eq.model.SourceAttribute.WEIGHT;
import static org.opensha.eq.model.SourceAttribute.WIDTH;
import static org.opensha.eq.model.SourceElement.AREA_SOURCE_SET;
import static org.opensha.eq.model.SourceElement.BORDER;
import static org.opensha.eq.model.SourceElement.FAULT_SOURCE_SET;
import static org.opensha.eq.model.SourceElement.GEOMETRY;
import static org.opensha.eq.model.SourceElement.INCREMENTAL_MFD;
import static org.opensha.eq.model.SourceElement.SETTINGS;
import static org.opensha.eq.model.SourceElement.SOURCE;
import static org.opensha.eq.model.SourceElement.SOURCE_PROPERTIES;
import static org.opensha.eq.model.SourceElement.TRACE;
import static org.opensha.eq.model.SourceType.AREA;
import static org.opensha.eq.model.SourceType.FAULT;
import static org.opensha.mfd.MfdType.INCR;
import static org.opensha.mfd.MfdType.SINGLE;
import static org.opensha.util.Parsing.addAttribute;
import static org.opensha.util.Parsing.addComment;
import static org.opensha.util.Parsing.addElement;
import gov.usgs.earthquake.model.GmmCreator;
import gov.usgs.earthquake.peer.PeerTestData.Fault;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.opensha.mfd.IncrementalMfd;
import org.opensha.util.Parsing;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
public class PeerModelMaker {

	static final String FAULT1_SOURCE = "PEER: Fault 1";
	static final String FAULT2_SOURCE = "PEER: Fault 2";
	static final String AREA1_SOURCE = "PEER: Area 1";

	static final String MODEL_DIR = "models/PEER";
	static final String SOURCE_FILE = "source.xml";
	static final String GMM_FILE = "gmm.xml";

	private final DocumentBuilder docBuilder;

	private PeerModelMaker() throws ParserConfigurationException {
		docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
	}

	public static void main(String[] args) throws Exception {
		PeerModelMaker pmm = new PeerModelMaker();
		pmm.writeModels();
	}

	void writeModels() throws Exception {
		Path path = null;

		// Set 1

		path = Paths.get(MODEL_DIR, S1_C1, FAULT.toString());
		write(path.resolve(SOURCE_FILE), createFault(S1_C1, F1, F1_SINGLE_6P5_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), SADIGH_GMM, GMM_CUTOFFS, null, null);

		path = Paths.get(MODEL_DIR, S1_C2, FAULT.toString());
		write(path.resolve(SOURCE_FILE), createFault(S1_C2, F1, F1_SINGLE_6P0_FLOAT_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), SADIGH_GMM, GMM_CUTOFFS, null, null);

		// TODO needs to be able to handle ruptureScaling sigma
		path = Paths.get(MODEL_DIR, S1_C3, FAULT.toString());
		write(path.resolve(SOURCE_FILE), createFault(S1_C3, F1, F1_SINGLE_6P0_FLOAT_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), SADIGH_GMM, GMM_CUTOFFS, null, null);

		path = Paths.get(MODEL_DIR, S1_C4, FAULT.toString());
		write(path.resolve(SOURCE_FILE), createFault(S1_C4, F2, F2_SINGLE_6P0_FLOAT_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), SADIGH_GMM, GMM_CUTOFFS, null, null);

		path = Paths.get(MODEL_DIR, S1_C5, FAULT.toString());
		write(path.resolve(SOURCE_FILE), createFault(S1_C5, F1, F1_GR_FLOAT_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), SADIGH_GMM, GMM_CUTOFFS, null, null);

		path = Paths.get(MODEL_DIR, S1_C6, FAULT.toString());
		write(path.resolve(SOURCE_FILE), createFault(S1_C6, F1, F1_GAUSS_FLOAT_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), SADIGH_GMM, GMM_CUTOFFS, null, null);

		path = Paths.get(MODEL_DIR, S1_C7, FAULT.toString());
		write(path.resolve(SOURCE_FILE), createFault(S1_C7, F1, F1_YC_CHAR_FLOAT_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), SADIGH_GMM, GMM_CUTOFFS, null, null);

		// TODO how to handle gmm sigma overrides; all cases above should be
		// zero
		// cases below are sigma with various truncations
		path = Paths.get(MODEL_DIR, S1_C8A, FAULT.toString());
		write(path.resolve(SOURCE_FILE), createFault(S1_C8A, F1, F1_SINGLE_6P0_FLOAT_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), SADIGH_GMM, GMM_CUTOFFS, null, null);

		path = Paths.get(MODEL_DIR, S1_C8B, FAULT.toString());
		write(path.resolve(SOURCE_FILE), createFault(S1_C8B, F1, F1_SINGLE_6P0_FLOAT_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), SADIGH_GMM, GMM_CUTOFFS, null, null);

		path = Paths.get(MODEL_DIR, S1_C8C, FAULT.toString());
		write(path.resolve(SOURCE_FILE), createFault(S1_C8C, F1, F1_SINGLE_6P0_FLOAT_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), SADIGH_GMM, GMM_CUTOFFS, null, null);

		// TODO need to be able to specify point source model; check that for
		// actual point source
		// ruptureScaling is ignored
		path = Paths.get(MODEL_DIR, S1_C10, AREA.toString());
		write(path.resolve(SOURCE_FILE), createArea(S1_C10, AREA_GR_MFD, AREA_DEPTH_STR, 1));
		GmmCreator.write(path.resolve(GMM_FILE), SADIGH_GMM, GMM_CUTOFFS, null, null);

		path = Paths.get(MODEL_DIR, S1_C11, AREA.toString());
		write(path.resolve(SOURCE_FILE), createArea(S1_C11, AREA_GR_MFD, AREA_DEPTH_VAR_STR, 1));
		GmmCreator.write(path.resolve(GMM_FILE), SADIGH_GMM, GMM_CUTOFFS, null, null);

		// Set 2

		// deagg
		path = Paths.get(MODEL_DIR, S2_C1, AREA.toString());
		write(path.resolve(SOURCE_FILE), createArea(S2_C1, AREA_GR_MFD, AREA_DEPTH_VAR_STR, 2));
		GmmCreator.write(path.resolve(GMM_FILE), SADIGH_GMM, GMM_CUTOFFS, null, null);
		path = Paths.get(MODEL_DIR, S2_C1, FAULT.toString());
		write(path.resolve("source1.xml"), createFault(S2_C1, FB, FB_YC_CHAR_FLOAT_MFD));
		write(path.resolve("source2.xml"), createFault(S2_C1, FC, FC_YC_CHAR_FLOAT_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), SADIGH_GMM, GMM_CUTOFFS, null, null);

		// NGAW2
		path = Paths.get(MODEL_DIR, S2_C2A1, FAULT.toString());
		write(path.resolve(SOURCE_FILE), createFault(S2_C2A1, F3, F3_GR_FLOAT_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), ASK14_GMM, GMM_CUTOFFS, null, null);
		path = Paths.get(MODEL_DIR, S2_C2A2, FAULT.toString());
		write(path.resolve(SOURCE_FILE), createFault(S2_C2A2, F3, F3_GR_FLOAT_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), ASK14_GMM, GMM_CUTOFFS, null, null);

		path = Paths.get(MODEL_DIR, S2_C2B1, FAULT.toString());
		write(path.resolve(SOURCE_FILE), createFault(S2_C2B1, F3, F3_GR_FLOAT_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), BSSA14_GMM, GMM_CUTOFFS, null, null);
		path = Paths.get(MODEL_DIR, S2_C2B2, FAULT.toString());
		write(path.resolve(SOURCE_FILE), createFault(S2_C2B2, F3, F3_GR_FLOAT_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), BSSA14_GMM, GMM_CUTOFFS, null, null);
		
		path = Paths.get(MODEL_DIR, S2_C2C1, FAULT.toString());
		write(path.resolve(SOURCE_FILE), createFault(S2_C2C1, F3, F3_GR_FLOAT_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), CB14_GMM, GMM_CUTOFFS, null, null);
		path = Paths.get(MODEL_DIR, S2_C2C2, FAULT.toString());
		write(path.resolve(SOURCE_FILE), createFault(S2_C2C2, F3, F3_GR_FLOAT_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), CB14_GMM, GMM_CUTOFFS, null, null);
		
		path = Paths.get(MODEL_DIR, S2_C2D1, FAULT.toString());
		write(path.resolve(SOURCE_FILE), createFault(S2_C2D1, F3, F3_GR_FLOAT_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), CY14_GMM, GMM_CUTOFFS, null, null);
		path = Paths.get(MODEL_DIR, S2_C2D2, FAULT.toString());
		write(path.resolve(SOURCE_FILE), createFault(S2_C2D2, F3, F3_GR_FLOAT_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), CY14_GMM, GMM_CUTOFFS, null, null);

		// hanging wall
		path = Paths.get(MODEL_DIR, S2_C3A, FAULT.toString());
		write(path.resolve(SOURCE_FILE), createFault(S2_C3A, F4, F4_SINGLE_7P0_FLOAT_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), ASK14_GMM, GMM_CUTOFFS, null, null);

		path = Paths.get(MODEL_DIR, S2_C3B, FAULT.toString());
		write(path.resolve(SOURCE_FILE), createFault(S2_C3B, F4, F4_SINGLE_7P0_FLOAT_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), BSSA14_GMM, GMM_CUTOFFS, null, null);

		path = Paths.get(MODEL_DIR, S2_C3C, FAULT.toString());
		write(path.resolve(SOURCE_FILE), createFault(S2_C3C, F4, F4_SINGLE_7P0_FLOAT_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), CB14_GMM, GMM_CUTOFFS, null, null);

		path = Paths.get(MODEL_DIR, S2_C3D, FAULT.toString());
		write(path.resolve(SOURCE_FILE), createFault(S2_C3D, F4, F4_SINGLE_7P0_FLOAT_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), CY14_GMM, GMM_CUTOFFS, null, null);
		
		// uniform vs triangular distribution
		path = Paths.get(MODEL_DIR, S2_C4A, FAULT.toString());
		write(path.resolve(SOURCE_FILE), createFault(S2_C4A, F5, F5_SINGLE_6P0_FLOAT_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), CY14_GMM, GMM_CUTOFFS, null, null);

		path = Paths.get(MODEL_DIR, S2_C4B, FAULT.toString());
		write(path.resolve(SOURCE_FILE), createFault(S2_C4B, F5, F5_SINGLE_6P0_FLOAT_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), CY14_GMM, GMM_CUTOFFS, null, null);
		
		// mixture model
		path = Paths.get(MODEL_DIR, S2_C5A, FAULT.toString());
		write(path.resolve(SOURCE_FILE), createFault(S2_C5A, F6, F6_SINGLE_6P0_FLOAT_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), CY14_GMM, GMM_CUTOFFS, null, null);
		
		path = Paths.get(MODEL_DIR, S2_C5B, FAULT.toString());
		write(path.resolve(SOURCE_FILE), createFault(S2_C5B, F6, F6_SINGLE_6P0_FLOAT_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), CY14_GMM, GMM_CUTOFFS, null, null);

	}

	private Document createFault(String testName, Fault fault, IncrementalMfd mfd) {

		Document doc = docBuilder.newDocument();
		doc.setXmlStandalone(true);
		Element root = doc.createElement(FAULT_SOURCE_SET.toString());
		addAttribute(NAME, testName, root);
		addAttribute(WEIGHT, 1.0, root);
		doc.appendChild(root);

		addComment(COMMENTS.get(testName), root);

		Element settings = addElement(SETTINGS, root);

		Element propsElem = addElement(SOURCE_PROPERTIES, settings);
		addAttribute(RUPTURE_SCALING, PEER, propsElem);

		Element srcElem = addElement(SOURCE, root);
		addAttribute(NAME, fault.name, srcElem);
		addMfd(mfd, srcElem);

		Element geom = addElement(GEOMETRY, srcElem);

		addAttribute(DIP, fault.dip, geom);
		addAttribute(WIDTH, fault.width, geom);
		addAttribute(RAKE, fault.rake, geom);
		addAttribute(DEPTH, fault.depth, geom);
		Element traceElem = addElement(TRACE, geom);
		traceElem.setTextContent(fault.trace.toString());

		return doc;
	}

	private Document createArea(String testName, IncrementalMfd mfd, String magDepthMap, int id) {
		Document doc = docBuilder.newDocument();
		doc.setXmlStandalone(true);
		Element root = doc.createElement(AREA_SOURCE_SET.toString());
		addAttribute(NAME, testName, root);
		addAttribute(WEIGHT, 1.0, root);
		doc.appendChild(root);

		addComment(COMMENTS.get(testName), root);

		Element srcElem = addElement(SOURCE, root);
		addAttribute(NAME, "Area Source " + id, srcElem);
		addMfd(mfd, srcElem);
		Element propsElem = addElement(SOURCE_PROPERTIES, srcElem);
		addAttribute(MAG_DEPTH_MAP, magDepthMap, propsElem);
		addAttribute(RUPTURE_SCALING, PEER, propsElem);
		Element border = addElement(BORDER, srcElem);
		border.setTextContent(S1_AREA_SOURCE_BORDER.toString());

		return doc;
	}

	private static void write(Path dest, Document doc) throws IOException, TransformerException {
		// write the content into xml file
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer trans = transformerFactory.newTransformer();
		trans.setOutputProperty(OutputKeys.INDENT, "yes");
		trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		trans.setOutputProperty(OutputKeys.STANDALONE, "yes");

		DOMSource source = new DOMSource(doc);
		Files.createDirectories(dest.getParent());
		StreamResult result = new StreamResult(Files.newOutputStream(dest));

		trans.transform(source, result);
	}

	private static void addMfd(IncrementalMfd mfd, Element e) {
		Element mfdElem = addElement(INCREMENTAL_MFD, e);
		if (mfd.getNum() == 1) {
			addAttribute(TYPE, SINGLE, mfdElem);
			addAttribute(A, String.format("%.8g", mfd.yValues().get(0)), mfdElem);
			addAttribute(M, String.format("%.3f", mfd.xValues().get(0)), mfdElem);
		} else {
			addAttribute(TYPE, INCR, mfdElem);
			addAttribute(RATES, Parsing.toString(mfd.yValues(), "%.8g"), mfdElem);
			addAttribute(MAGS, Parsing.toString(mfd.xValues(), "%.3f"), mfdElem);
		}
		addAttribute(FLOATS, mfd.floats(), mfdElem);
		addAttribute(WEIGHT, 1.0, mfdElem);
	}

}
