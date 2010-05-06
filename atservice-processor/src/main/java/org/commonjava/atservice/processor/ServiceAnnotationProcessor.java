/*
 *  Copyright (C) 2010 John Casey.
 *  
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *  
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.commonjava.atservice.processor;

import org.commonjava.atservice.annotation.Service;

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
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import javax.tools.Diagnostic.Kind;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

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
            final String impl = tel.getQualifiedName() == null ? null : tel.getQualifiedName().toString();

            if ( impl == null )
            {
                continue;
            }

            String serviceName = null;
            for ( final AnnotationMirror aMirror : el.getAnnotationMirrors() )
            {
                final String aName =
                    ( (TypeElement) aMirror.getAnnotationType().asElement() ).getQualifiedName().toString();

                if ( !aName.equals( Service.class.getName() ) )
                {
                    continue;
                }

                final AnnotationValue value = aMirror.getElementValues().values().iterator().next();
                serviceName = value.toString();
                if ( serviceName.endsWith( ".class" ) )
                {
                    serviceName = serviceName.substring( 0, serviceName.length() - ".class".length() );
                }
            }

            if ( serviceName == null )
            {
                continue;
            }

            Set<String> impls = implMap.get( serviceName );
            if ( impls == null )
            {
                impls = new LinkedHashSet<String>();
                implMap.put( serviceName, impls );
            }
            impls.add( impl );
        }

        for ( final Map.Entry<String, Set<String>> serviceEntry : implMap.entrySet() )
        {
            final Filer filer = processingEnv.getFiler();
            Writer writer = null;
            try
            {
                final FileObject file =
                    filer.createResource( StandardLocation.CLASS_OUTPUT, "", "META-INF/services/"
                        + serviceEntry.getKey(), (Element[]) null );

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
