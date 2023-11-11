/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.mavenplugin.test.coverage.report.sonar.model;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlSchemaType;
import jakarta.xml.bind.annotation.XmlType;

//
//This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802
//See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a>
//Any modifications to this file will be lost upon recompilation of the source schema.
//Generated on: 2021.03.19 at 01:42:01 PM CET
//

import lombok.Generated;

// @formatter:off
/**
 * <p>
 * Java class for anonymous complex type.
 *
 * <p>
 * The following schema fragment specifies the expected content contained within
 * this class.
 * <p>
 * {@code
 * <pre>
 * <complexType>
 *   <complexContent>
 *     <restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       <sequence>
 *         <element name="file" maxOccurs="unbounded" minOccurs="0">
 *           <complexType>
 *             <complexContent>
 *               <restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *                 <sequence>
 *                   <element name="lineToCover" maxOccurs="unbounded" minOccurs="0">
 *                     <complexType>
 *                       <complexContent>
 *                         <restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *                           <attribute name="lineNumber" use="required" type="{http://www.w3.org/2001/XMLSchema}positiveInteger" />
 *                           <attribute name="covered" use="required" type="{http://www.w3.org/2001/XMLSchema}boolean" />
 *                           <attribute name="branchesToCover" type="{http://www.w3.org/2001/XMLSchema}nonNegativeInteger" />
 *                           <attribute name="coveredBranches" type="{http://www.w3.org/2001/XMLSchema}nonNegativeInteger" />
 *                         </restriction>
 *                       </complexContent>
 *                     </complexType>
 *                   </element>
 *                 </sequence>
 *                 <attribute name="path" use="required" type="{http://www.w3.org/2001/XMLSchema}string" />
 *               </restriction>
 *             </complexContent>
 *           </complexType>
 *         </element>
 *       </sequence>
 *       <attribute name="version" use="required" type="{http://www.w3.org/2001/XMLSchema}positiveInteger" />
 *     </restriction>
 *   </complexContent>
 * </complexType>
 * </pre>
 * }
 *
 */
// @formatter:on
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = { "file" })
@XmlRootElement(name = "coverage")
@Generated
public class Coverage {

    protected List<Coverage.File> file;

    @XmlAttribute(name = "version", required = true)
    @XmlSchemaType(name = "positiveInteger")
    protected BigInteger version;

    // @formatter:off
    /**
     * Gets the value of the file property.
     *
     * <p>
     * This accessor method returns a reference to the live list, not a snapshot.
     * Therefore, any modification you make to the returned list will be present
     * inside the JAXB object. This is why there is not a <CODE>set</CODE> method
     * for the file property.
     *
     * <p>
     * For example, to add a new item, do as follows: {@code
     * <pre>
     * getFile().add(newItem);
     * </pre>
     *}
     *
     * <p>
     *
     * Objects of the following type(s) are allowed in the list
     * {@link Coverage.File }
     *
     * @return list of the coverage files
     *
     *
     */
	 // @formatter:on
    public List<Coverage.File> getFile() {
        if (file == null) {
            file = new ArrayList<>();
        }
        return this.file;
    }

    /**
     * Gets the value of the version property.
     *
     * @return possible object is {@link BigInteger }
     *
     */
    public BigInteger getVersion() {
        return version;
    }

    /**
     * Sets the value of the version property.
     *
     * @param value allowed object is {@link BigInteger }
     *
     */
    public void setVersion(BigInteger value) {
        this.version = value;
    }

    // @formatter:off
    /**
     * <p>
     * Java class for anonymous complex type.
     *
     * <p>
     * The following schema fragment specifies the expected content contained within
     * this class. {@code
     * <pre>
     * <complexType>
     *   <complexContent>
     *     <restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
     *       <sequence>
     *         <element name="lineToCover" maxOccurs="unbounded" minOccurs="0">
     *           <complexType>
     *             <complexContent>
     *               <restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
     *                 <attribute name="lineNumber" use="required" type="{http://www.w3.org/2001/XMLSchema}positiveInteger" />
     *                 <attribute name="covered" use="required" type="{http://www.w3.org/2001/XMLSchema}boolean" />
     *                 <attribute name="branchesToCover" type="{http://www.w3.org/2001/XMLSchema}nonNegativeInteger" />
     *                 <attribute name="coveredBranches" type="{http://www.w3.org/2001/XMLSchema}nonNegativeInteger" />
     *               </restriction>
     *             </complexContent>
     *           </complexType>
     *         </element>
     *       </sequence>
     *       <attribute name="path" use="required" type="{http://www.w3.org/2001/XMLSchema}string" />
     *     </restriction>
     *   </complexContent>
     * </complexType>
     * </pre>
     *
     * }
     *
     */
	 // @formatter:on
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "", propOrder = { "lineToCover" })
    @Generated
    public static class File {

        protected List<Coverage.File.LineToCover> lineToCover;

        @XmlAttribute(name = "path", required = true)
        protected String path;

        // @formatter:off
        /**
         * Gets the value of the lineToCover property.
         *
         * <p>
         * This accessor method returns a reference to the live list, not a snapshot.
         * Therefore, any modification you make to the returned list will be present
         * inside the JAXB object. This is why there is not a <CODE>set</CODE> method
         * for the lineToCover property.
         *
         * <p>
         * For example, to add a new item, do as follows: {@code
         * <pre>
         * getLineToCover().add(newItem);
         * </pre>
         * }
         *
         * <p>
         * Objects of the following type(s) are allowed in the list
         * {@link Coverage.File.LineToCover }
         *
         * @return the line to converter
         *
         *
         */
		// @formatter:on
        public List<Coverage.File.LineToCover> getLineToCover() {
            if (lineToCover == null) {
                lineToCover = new ArrayList<>();
            }
            return this.lineToCover;
        }

        /**
         * Gets the value of the path property.
         *
         * @return possible object is {@link String }
         *
         */
        public String getPath() {
            return path;
        }

        /**
         * Sets the value of the path property.
         *
         * @param value allowed object is {@link String }
         *
         */
        public void setPath(String value) {
            this.path = value;
        }

        // @formatter:off
        /**
         * <p>
         * Java class for anonymous complex type.
         *
         * <p>
         * The following schema fragment specifies the expected content contained within
         * this class. {@code
         * <pre>
         * <complexType>
         *   <complexContent>
         *     <restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
         *       <attribute name="lineNumber" use="required" type="{http://www.w3.org/2001/XMLSchema}positiveInteger" />
         *       <attribute name="covered" use="required" type="{http://www.w3.org/2001/XMLSchema}boolean" />
         *       <attribute name="branchesToCover" type="{http://www.w3.org/2001/XMLSchema}nonNegativeInteger" />
         *       <attribute name="coveredBranches" type="{http://www.w3.org/2001/XMLSchema}nonNegativeInteger" />
         *     </restriction>
         *   </complexContent>
         * </complexType>
         * </pre>
         *
         * }
         *
         */
		 // @formatter:on
        @XmlAccessorType(XmlAccessType.FIELD)
        @XmlType(name = "")
        @Generated
        public static class LineToCover {

            @XmlAttribute(name = "lineNumber", required = true)
            @XmlSchemaType(name = "positiveInteger")
            protected BigInteger lineNumber;

            @XmlAttribute(name = "covered", required = true)
            protected boolean covered;

            @XmlAttribute(name = "branchesToCover")
            @XmlSchemaType(name = "nonNegativeInteger")
            protected BigInteger branchesToCover;

            @XmlAttribute(name = "coveredBranches")
            @XmlSchemaType(name = "nonNegativeInteger")
            protected BigInteger coveredBranches;

            /**
             * Gets the value of the lineNumber property.
             *
             * @return possible object is {@link BigInteger }
             *
             */
            public BigInteger getLineNumber() {
                return lineNumber;
            }

            /**
             * Sets the value of the lineNumber property.
             *
             * @param value allowed object is {@link BigInteger }
             *
             */
            public void setLineNumber(BigInteger value) {
                this.lineNumber = value;
            }

            /**
             * Gets the value of the covered property.
             *
             * @return true if covered
             *
             */
            public boolean isCovered() {
                return covered;
            }

            /**
             * Sets the value of the covered property.
             *
             * @param value indicate if covered
             *
             */
            public void setCovered(boolean value) {
                this.covered = value;
            }

            /**
             * Gets the value of the branchesToCover property.
             *
             * @return possible object is {@link BigInteger }
             *
             */
            public BigInteger getBranchesToCover() {
                return branchesToCover;
            }

            /**
             * Sets the value of the branchesToCover property.
             *
             * @param value allowed object is {@link BigInteger }
             *
             */
            public void setBranchesToCover(BigInteger value) {
                this.branchesToCover = value;
            }

            /**
             * Gets the value of the coveredBranches property.
             *
             * @return possible object is {@link BigInteger }
             *
             */
            public BigInteger getCoveredBranches() {
                return coveredBranches;
            }

            /**
             * Sets the value of the coveredBranches property.
             *
             * @param value allowed object is {@link BigInteger }
             *
             */
            public void setCoveredBranches(BigInteger value) {
                this.coveredBranches = value;
            }

        }

    }

}
