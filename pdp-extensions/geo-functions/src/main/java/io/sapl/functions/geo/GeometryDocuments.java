/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
package io.sapl.functions.geo;

import lombok.val;
import lombok.experimental.UtilityClass;
import org.geotools.xsd.Configuration;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import org.xml.sax.helpers.DefaultHandler;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import static io.sapl.functions.geo.GeographicFunctionLibrary.hardenedParser;

/** Reads each XML geometry independently so feature schemas cannot coerce its kind. */
@UtilityClass
class GeometryDocuments {
    private static final String          ERROR_INVALID_DOCUMENT = "Invalid geographic XML document.";
    private static final Set<String>     GEOMETRIES             = Set.of("Point", "LineString", "LinearRing", "Polygon",
            "MultiPoint", "MultiLineString", "MultiPolygon", "MultiGeometry", "MultiCurve", "MultiSurface");
    private static final GeometryFactory FACTORY                = new GeometryFactory();

    static List<Geometry> read(String text, Configuration configuration, boolean kml)
            throws ParserConfigurationException, SAXException, IOException, TransformerException {
        val factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setAttribute("http://www.oracle.com/xml/jaxp/properties/maxElementDepth", 500);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        val builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new DefaultHandler());
        val root   = builder.parse(new InputSource(new StringReader(text))).getDocumentElement();
        val result = new ArrayList<Geometry>();
        collect(root, configuration, kml, result, 0);
        return result;
    }

    private static void collect(Element element, Configuration configuration, boolean kml, List<Geometry> result,
            int depth) throws ParserConfigurationException, SAXException, IOException, TransformerException {
        if (depth >= 500) {
            throw new IllegalArgumentException(ERROR_INVALID_DOCUMENT);
        }
        if (GEOMETRIES.contains(element.getLocalName())) {
            if (!kml || element.getParentNode().getNodeType() == Node.DOCUMENT_NODE) {
                result.add(geometryOf(element, configuration));
            }
            return;
        }
        if (kml && "Placemark".equals(element.getLocalName())) {
            val members = new ArrayList<Geometry>();
            for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof Element geometry && GEOMETRIES.contains(geometry.getLocalName())) {
                    members.add(geometryOf(geometry, configuration));
                }
            }
            if (members.size() == 1) {
                result.addAll(members);
            } else if (!members.isEmpty()) {
                result.add(FACTORY.createGeometryCollection(members.toArray(Geometry[]::new)));
            }
            return;
        }
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element nested) {
                collect(nested, configuration, kml, result, depth + 1);
            }
        }
    }

    private static Geometry geometryOf(Element element, Configuration configuration)
            throws ParserConfigurationException, SAXException, IOException, TransformerException {
        normalizeCoordinates(element, configuration instanceof org.geotools.gml3.GMLConfiguration);
        val factory = TransformerFactory.newInstance();
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        val text = new StringWriter();
        factory.newTransformer().transform(new DOMSource(element), new StreamResult(text));
        val parsed = hardenedParser(configuration).parse(new StringReader(text.toString()));
        if (parsed instanceof Geometry geometry) {
            return geometry;
        }
        throw new IllegalArgumentException(ERROR_INVALID_DOCUMENT);
    }

    private static void normalizeCoordinates(Element element, boolean gml3) {
        if (gml3 && ("outerBoundaryIs".equals(element.getLocalName())
                || "innerBoundaryIs".equals(element.getLocalName()))) {
            val localName = "outerBoundaryIs".equals(element.getLocalName()) ? "exterior" : "interior";
            val name      = element.getPrefix() == null ? localName : element.getPrefix() + ":" + localName;
            element.getOwnerDocument().renameNode(element, element.getNamespaceURI(), name);
        }
        if ("coordinates".equals(element.getLocalName())) {
            element.setTextContent(normalizeCoordinateSeparators(element.getTextContent()));
        }
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element nested) {
                normalizeCoordinates(nested, gml3);
            }
        }
    }

    private static String normalizeCoordinateSeparators(String text) {
        val result     = new StringBuilder(text.length());
        var afterComma = false;
        for (var index = 0; index < text.length(); index++) {
            val character = text.charAt(index);
            if (character == ',') {
                while (!result.isEmpty() && isCoordinateWhitespace(result.charAt(result.length() - 1))) {
                    result.setLength(result.length() - 1);
                }
                result.append(character);
                afterComma = true;
            } else if (!afterComma || !isCoordinateWhitespace(character)) {
                result.append(character);
                afterComma = false;
            }
        }
        return result.toString();
    }

    private static boolean isCoordinateWhitespace(char character) {
        return character == ' ' || character >= '\t' && character <= '\r';
    }
}
