/*
 * Copyright 2010 Red Hat, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.commonjava.atservice.processor;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import org.commonjava.atservice.annotation.Service;

@SupportedAnnotationTypes( { "org.commonjava.atservice.annotation.Service" } )
@SupportedSourceVersion( SourceVersion.RELEASE_6 )
public class ServiceAnnotationProcessor
    extends AbstractProcessor
{

    private static final String LS = "\n";

    @Override
    public boolean process( final Set<? extends TypeElement> typeElements, final RoundEnvironment roundEnvironment )
    {
        final Messager logger = processingEnv.getMessager();

        final Map<String, Set<String>> implMap = new HashMap<String, Set<String>>();
        for ( final Element el : roundEnvironment.getElementsAnnotatedWith( Service.class ) )
        {
            final TypeElement tel = (TypeElement) el;
            final String impl = tel.getQualifiedName() == null ? null : tel.getQualifiedName()
                                                                           .toString();

            if ( impl == null )
            {
                continue;
            }

            final Set<String> serviceNames = new HashSet<String>();
            for ( final AnnotationMirror aMirror : el.getAnnotationMirrors() )
            {
                final String aName = ( (TypeElement) aMirror.getAnnotationType()
                                                            .asElement() ).getQualifiedName()
                                                                          .toString();

                if ( !aName.equals( Service.class.getName() ) )
                {
                    continue;
                }

                final Iterator<? extends AnnotationValue> valueIterator = aMirror.getElementValues()
                                                                                 .values()
                                                                                 .iterator();
                while ( valueIterator.hasNext() )
                {
                    final AnnotationValue value = valueIterator.next();
                    String s = value.toString();
                    String[] ss = null;

                    if ( s.startsWith( "{" ) )
                    {
                        s = s.substring( 1, s.length() - 1 );
                        ss = s.split( "\\s*,\\s*" );
                    }
                    else
                    {
                        ss = new String[] { s };
                    }

                    for ( final String className : ss )
                    {
                        serviceNames.add( className.substring( 0, className.length() - ".class".length() ) );
                    }
                }
            }

            if ( serviceNames.isEmpty() )
            {
                final List<? extends TypeMirror> interfaces = tel.getInterfaces();
                for ( final TypeMirror type : interfaces )
                {
                    serviceNames.add( type.toString() );
                }
            }

            for ( final String serviceName : serviceNames )
            {
                Set<String> impls = implMap.get( serviceName );
                if ( impls == null )
                {
                    impls = new LinkedHashSet<String>();
                    implMap.put( serviceName, impls );
                }
                impls.add( impl );
            }
        }

        for ( final Map.Entry<String, Set<String>> serviceEntry : implMap.entrySet() )
        {
            final Filer filer = processingEnv.getFiler();
            Writer writer = null;
            try
            {
                final FileObject file =
                    filer.createResource( StandardLocation.CLASS_OUTPUT, "",
                                          "META-INF/services/" + serviceEntry.getKey(), (Element[]) null );

                writer = new OutputStreamWriter( file.openOutputStream(), Charset.forName( "UTF-8" ) );

                for ( final String impl : serviceEntry.getValue() )
                {
                    writer.write( impl );
                    writer.write( LS );
                }
            }
            catch ( final IOException e )
            {
                logger.printMessage( Kind.ERROR, "While writing services entry for: '" + serviceEntry.getKey()
                    + "', error: " + e.getMessage() );
            }
            finally
            {
                if ( writer != null )
                {
                    try
                    {
                        writer.close();
                    }
                    catch ( final IOException e )
                    {
                    }
                }
            }
        }

        return true;
    }
}
