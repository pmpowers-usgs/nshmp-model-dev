package gov.usgs.earthquake.model;

import static com.google.common.base.Preconditions.checkArgument;
import static org.opensha.gmm.GmmAttribute.ID;
import static org.opensha.gmm.GmmAttribute.MAX_DISTANCE;
import static org.opensha.gmm.GmmAttribute.VALUES;
import static org.opensha.gmm.GmmAttribute.WEIGHT;
import static org.opensha.gmm.GmmAttribute.WEIGHTS;
import static org.opensha.gmm.GmmElement.GROUND_MOTION_MODELS;
import static org.opensha.gmm.GmmElement.MODEL;
import static org.opensha.gmm.GmmElement.MODEL_SET;
import static org.opensha.gmm.GmmElement.UNCERTAINTY;
import static org.opensha.util.Parsing.addAttribute;
import static org.opensha.util.Parsing.addElement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

import org.opensha.gmm.Gmm;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
public class GmmCreator {

	public static void writeFile(Path dest, List<Map<Gmm, Double>> gmmMapList, List<Double> cutoffList,
			double[] uncValues, double[] uncWeights) throws ParserConfigurationException,
			TransformerException, IOException {

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
		for (Map<Gmm, Double> gmmMap : gmmMapList) {
			Element gmmSetElem = addElement(MODEL_SET, root);
			addAttribute(MAX_DISTANCE, cutoffList.get(count++), gmmSetElem);
			for (Entry<Gmm, Double> entry : gmmMap.entrySet()) {
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
		Files.createDirectories(dest.getParent());
		StreamResult result = new StreamResult(Files.newOutputStream(dest));
		trans.transform(source, result);
	}

}
