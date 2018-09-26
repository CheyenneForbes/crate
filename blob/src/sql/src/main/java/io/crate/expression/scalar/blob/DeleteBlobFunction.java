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
import io.crate.types.DataTypes;
import org.apache.lucene.util.BytesRef;

import javax.annotation.Nullable;
import java.util.List;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.impl.client.DefaultHttpClient;

public class DeleteBlobFunction extends Scalar<Boolean, BytesRef> {

    public static final String NAME = "delblob";

    public static void register(ScalarFunctionModule module) {
        module.register(NAME, new Resolver());
    }

    private final FunctionInfo info;

    public DeleteBlobFunction(DataType dataTypeIn) {
        this.info = new FunctionInfo(
            new FunctionIdent(NAME, ImmutableList.of(dataTypeIn)), DataTypes.BOOLEAN);
    }

    @Override
    public FunctionInfo info() {
        return info;
    }

    @Override
    public final Boolean evaluate(Input<BytesRef>... args) {
        try {
            if (args.length == 1) {
                HttpDelete httpdelete = new HttpDelete("http://localhost:4200/_blobs/".concat(args[0].value().utf8ToString()).replaceAll("\\s","")); 
                HttpClient client = new DefaultHttpClient();
                HttpResponse response = client.execute(httpdelete);
            } else if (args.length == 2) {
                final Matcher m = Pattern.compile(args[1].value().utf8ToString()).matcher(args[0].value().utf8ToString());
                while (m.find()) {
                    HttpDelete httpdelete = new HttpDelete("http://localhost:4200/_blobs/".concat(m.group(0)).replaceAll("\\s","")); 
                    HttpClient client = new DefaultHttpClient();
                    HttpResponse response = client.execute(httpdelete);
                }
            } else {
                throw new IllegalArgumentException("Wrong number of arguments");
            }
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private static class Resolver implements FunctionResolver {

        private final FuncParams funcParams = FuncParams.builder(Param.STRING).build();

        @Override
        public FunctionImplementation getForTypes(List<DataType> symbols) throws IllegalArgumentException {
            return new DeleteBlobFunction(symbols.get(0));
        }

        @Nullable
        @Override
        public List<DataType> getSignature(List<? extends FuncArg> symbols) {
            return funcParams.match(symbols);
        }
    }
}
