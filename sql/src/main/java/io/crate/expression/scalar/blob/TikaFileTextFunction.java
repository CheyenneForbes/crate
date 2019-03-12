/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package io.crate.expression.scalar.blob;

import com.google.common.collect.ImmutableList;
import io.crate.expression.symbol.FuncArg;
import io.crate.data.Input;
import io.crate.metadata.functions.params.FuncParams;
import io.crate.metadata.FunctionIdent;
import io.crate.metadata.FunctionImplementation;
import io.crate.metadata.FunctionInfo;
import io.crate.metadata.FunctionResolver;
import io.crate.metadata.Scalar;
import io.crate.metadata.functions.params.Param;
import io.crate.expression.scalar.ScalarFunctionModule;
import io.crate.types.DataType;
import org.apache.lucene.util.BytesRef;

import javax.annotation.Nullable;
import java.util.List;

import java.io.InputStream;

import java.io.IOException;

import org.apache.tika.exception.TikaException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.SAXException;

public class TikaFileTextFunction extends Scalar<BytesRef, BytesRef> {

    public static final String NAME = "tikatext";

    public static void register(ScalarFunctionModule module) {
        module.register(NAME, new Resolver());
    }

    private final FunctionInfo info;

    public TikaFileTextFunction(DataType dataType) {
        this.info = new FunctionInfo(
            new FunctionIdent(NAME, ImmutableList.of(dataType)), dataType);
    }

    @Override
    public FunctionInfo info() {
        return info;
    }

    @Override
    public final BytesRef evaluate(Input<BytesRef>... args) {
        try {
            if (args.length == 1) {
                HttpGet httpget = new HttpGet("http://localhost:4200/_blobs/".concat(((BytesRef)args[0].value()).utf8ToString()).replaceAll("\\s","")); 
                HttpEntity entity = null;
                HttpClient client = new DefaultHttpClient();
                HttpResponse response = client.execute(httpget);
                entity = response.getEntity();
                if (entity == null) {
                    return new BytesRef();
                } else if (entity.getContentLength() > (1048576 * 100)) {
                    return new BytesRef();
                } else {
                    InputStream instream = entity.getContent();
                    BodyContentHandler handler = new BodyContentHandler();
                    Metadata metadata = new Metadata();
                    Parser parser = new AutoDetectParser();
                    parser.parse(instream, handler, metadata, new ParseContext());
                    return new BytesRef(handler.toString());
                }
            } else {
                throw new IllegalArgumentException("Wrong number of arguments");
            }
        } catch (IOException | TikaException | SAXException ex) {
            return new BytesRef();
        }
    }

    private static class Resolver implements FunctionResolver {

        private final FuncParams funcParams = FuncParams.builder(Param.STRING).build();

        @Override
        public FunctionImplementation getForTypes(List<DataType> symbols) throws IllegalArgumentException {
            return new TikaFileTextFunction(symbols.get(0));
        }

        @Nullable
        @Override
        public List<DataType> getSignature(List<? extends FuncArg> symbols) {
            return funcParams.match(symbols);
        }
    }
}
