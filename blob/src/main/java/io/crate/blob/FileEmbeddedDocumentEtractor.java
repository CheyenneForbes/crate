/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.blob;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.mime.MediaType;
import org.apache.tika.detect.Detector;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;

import org.xml.sax.ContentHandler;

import org.apache.tika.extractor.EmbeddedDocumentExtractor;

import java.io.FileOutputStream;
import org.apache.commons.io.IOUtils;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.poifs.filesystem.DirectoryEntry;

class FileEmbeddedDocumentEtractor implements EmbeddedDocumentExtractor {
    private int count = 0;

    public final boolean shouldParseEmbedded(Metadata m) {
        return true;
    }

    public final void parseEmbedded(InputStream inputStream, ContentHandler contentHandler, Metadata metadata, boolean outputHtml) throws IOException {
        Detector detector = new DefaultDetector();
        String name = metadata.get("resourceName");
        MediaType contentType = detector.detect(inputStream, metadata);
        if (!contentType.getType().equals("image")) {
            return;
        }
        String embeddedFile = name;
        File outputFile = new File("C:\\toHtml\\images", embeddedFile);
        try {
            try (FileOutputStream os = new FileOutputStream(outputFile)) {
                TikaInputStream tin = (TikaInputStream)((inputStream instanceof TikaInputStream) ? inputStream : null);
                if (tin != null) {
                    if (tin.getOpenContainer() != null && tin.getOpenContainer() instanceof DirectoryEntry) {
                        POIFSFileSystem fs = new POIFSFileSystem();

                        fs.writeFilesystem(os);
                    } else {
                        IOUtils.copy(inputStream, os);
                    }
                }
            }
        } catch (FileNotFoundException ex) {
            throw ex;
        }
    }
}