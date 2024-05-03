package com.ortussolutions.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.stream.Collectors;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMultipart;

import org.apache.commons.mail.Email;
import org.apache.commons.mail.MultiPartEmail;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.compiler.parser.BoxSourceType;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.dynamic.casters.KeyCaster;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.VariablesScope;
import ortus.boxlang.runtime.util.FileSystemUtil;

public class MailTest {

	static BoxRuntime	instance;
	IBoxContext			context;
	IScope				variables;
	static Key			result			= new Key( "result" );
	static Key			messageId		= Key.of( "messageId" );
	static Key			messageVar		= Key.of( "messageVar" );
	static String		testURLImage	= "https://ortus-public.s3.amazonaws.com/logos/ortus-medium.jpg";
	static String		testBinaryFile	= "src/test/resources/tmp/MailTest/test.jpg";
	static String		tmpDirectory	= "src/test/resources/tmp/MailTest";

	@BeforeAll
	public static void setUp() throws IOException {
		instance = BoxRuntime.getInstance( true, Path.of( "src/test/resources/boxlang.json" ).toString() );
		System.out.println(
		    "Schedulers : " + instance.getSchedulerService().getSchedulers().entrySet().stream().map( entry -> KeyCaster.cast( entry.getKey() ).getName() )
		        .collect( Collectors.joining( "," ) ) );

		if ( !FileSystemUtil.exists( testBinaryFile ) ) {
			FileSystemUtil.createDirectory( tmpDirectory, true, null );
			BufferedInputStream urlStream = new BufferedInputStream( new URL( testURLImage ).openStream() );
			FileSystemUtil.write( testBinaryFile, urlStream.readAllBytes(), true );
			assertTrue( FileSystemUtil.exists( testBinaryFile ) );
		}
	}

	@BeforeEach
	public void setupEach() {
		context		= new ScriptingRequestBoxContext( instance.getRuntimeContext() );
		variables	= context.getScopeNearby( VariablesScope.name );
		variables.clear();
	}

	@AfterAll
	public static void teardown() throws IOException {

		if ( FileSystemUtil.exists( tmpDirectory ) ) {
			FileSystemUtil.deleteDirectory( tmpDirectory, true );
		}

	}

	@DisplayName( "It can test a basic sending of mail with no spool" )
	@Test
	public void testMailComponent() {
		instance.executeSource(
		    """
		                     	<cfmail
		    			from="jclausen@ortussolutions.com"
		    			to="jclausen@ortussolutions.com"
		    			subject="Mail Test"
		    			server="127.0.0.1"
		    			port="25"
		    			spoolEnable="false"
		    			debug="true"
		    messageIdentifier="messageId"
		    messageVariable="messageVar"
		    		>
		    Hello mail!
		    </cfmail>
		                     """,
		    context, BoxSourceType.CFTEMPLATE );
		assertTrue( variables.get( messageId ) instanceof String );
		assertTrue( variables.get( messageVar ) instanceof Email );
		Email message = ( Email ) variables.get( messageVar );
		assertEquals( "Hello mail!", StringCaster.cast( message.getContent() ).trim() );
		assertEquals( "Mail Test", StringCaster.cast( message.getSubject() ).trim() );
		assertEquals( "jclausen@ortussolutions.com", message.getToAddresses().get( 0 ).toString() );
		assertEquals( "jclausen@ortussolutions.com", message.getFromAddress().toString() );
	}

	@DisplayName( "It can test a basic sending of mail with bx script" )
	@Test
	public void testMailComponentBX() {
		instance.executeSource(
		    """
		                     	<bx:mail
		    			from="jclausen@ortussolutions.com"
		    			to="jclausen@ortussolutions.com"
		    			subject="Mail Test"
		    			server="127.0.0.1"
		    			port="25"
		    			spoolEnable="false"
		    			debug="true"
		    messageIdentifier="messageId"
		    messageVariable="messageVar"
		    		>
		    Hello mail!
		    </bx:mail>
		                     """,
		    context, BoxSourceType.BOXTEMPLATE );
		assertTrue( variables.get( messageId ) instanceof String );
		assertTrue( variables.get( messageVar ) instanceof Email );
		Email message = ( Email ) variables.get( messageVar );
		assertEquals( "Hello mail!", StringCaster.cast( message.getContent() ).trim() );
		assertEquals( "Mail Test", StringCaster.cast( message.getSubject() ).trim() );
		assertEquals( "jclausen@ortussolutions.com", message.getToAddresses().get( 0 ).toString() );
		assertEquals( "jclausen@ortussolutions.com", message.getFromAddress().toString() );
	}

	@DisplayName( "It can test a basic sending of mail with script" )
	@Test
	public void testMailComponentScript() {
		instance.executeSource(
		    """
		             mail
		     			from="jclausen@ortussolutions.com"
		     			to="jclausen@ortussolutions.com"
		     			subject="Mail Test"
		     			server="127.0.0.1"
		     			port="25"
		     			spoolEnable="false"
		     			debug="true"
		     messageIdentifier="messageId"
		     messageVariable="messageVar"
		     		{
		     writeOutput( "Hello mail!" );
		    }
		                      """,
		    context, BoxSourceType.BOXSCRIPT );
		assertTrue( variables.get( messageId ) instanceof String );
		assertTrue( variables.get( messageVar ) instanceof Email );
		Email message = ( Email ) variables.get( messageVar );
		assertEquals( "Hello mail!", StringCaster.cast( message.getContent() ).trim() );
		assertEquals( "Mail Test", StringCaster.cast( message.getSubject() ).trim() );
		assertEquals( "jclausen@ortussolutions.com", message.getToAddresses().get( 0 ).toString() );
		assertEquals( "jclausen@ortussolutions.com", message.getFromAddress().toString() );
	}

	@DisplayName( "It can test a basic sending of mail with component parts" )
	@Test
	public void testMailComponentParts() throws MessagingException, IOException {
		instance.executeSource(
		    """
		                        	<bx:mail
		       			from="jclausen@ortussolutions.com"
		       			to="jclausen@ortussolutions.com"
		       			subject="Mail Test"
		       			server="127.0.0.1"
		       			port="25"
		       			spoolEnable="false"
		       			debug="true"
		       messageIdentifier="messageId"
		       messageVariable="messageVar"
		       		>
		       <bx:mailpart type="text">
		    Hello mail!
		    </bx:mailpart>
		       <bx:mailpart type="html">
		    <h1>Hello mail!</h1>
		    </bx:mailpart>
		       </bx:mail>
		                        """,
		    context, BoxSourceType.BOXTEMPLATE );
		assertTrue( variables.get( messageId ) instanceof String );
		assertTrue( variables.get( messageVar ) instanceof Email );
		Email message = ( Email ) variables.get( messageVar );
		assertTrue( message.getEmailBody() instanceof MimeMultipart );
		assertEquals( "Hello mail!", message.getEmailBody().getBodyPart( 0 ).getContent().toString().trim() );
		assertEquals( "text/plain;charset=utf-8", message.getEmailBody().getBodyPart( 0 ).getContentType().toString() );
		assertEquals( "<h1>Hello mail!</h1>", message.getEmailBody().getBodyPart( 1 ).getContent().toString().trim() );
		assertEquals( "text/html;charset=utf-8", message.getEmailBody().getBodyPart( 1 ).getContentType().toString() );
		assertEquals( "Mail Test", StringCaster.cast( message.getSubject() ).trim() );
		assertEquals( "jclausen@ortussolutions.com", message.getToAddresses().get( 0 ).toString() );
		assertEquals( "jclausen@ortussolutions.com", message.getFromAddress().toString() );
	}

	@DisplayName( "It can test a basic sending of mail with a mime attachment" )
	@Test
	public void testMailMimeAttach() {
		variables.put( Key.of( "testFile" ), testBinaryFile );
		instance.executeSource(
		    """
		                          	<bx:mail
		         			from="jclausen@ortussolutions.com"
		         			to="jclausen@ortussolutions.com"
		         			subject="Mail Test"
		         			server="127.0.0.1"
		         			port="25"
		         			spoolEnable="false"
		         			debug="true"
		      mimeAttach="#testFile#"
		         messageIdentifier="messageId"
		         messageVariable="messageVar"
		         		>
		    Here's an image!
		         </bx:mail>
		                          """,
		    context, BoxSourceType.BOXTEMPLATE );
		assertTrue( variables.get( messageId ) instanceof String );
		assertTrue( variables.get( messageVar ) instanceof Email );
		Email message = ( Email ) variables.get( messageVar );
		assertEquals( "Here's an image!", StringCaster.cast( message.getContent() ).trim() );
		assertTrue( ( ( MultiPartEmail ) message ).isBoolHasAttachments() );
		assertEquals( "jclausen@ortussolutions.com", message.getToAddresses().get( 0 ).toString() );
		assertEquals( "jclausen@ortussolutions.com", message.getFromAddress().toString() );
	}

	@DisplayName( "It can test a basic sending of mail with a mime attachment" )
	@Test
	public void testMultiPartWithFile() throws MessagingException, IOException {
		variables.put( Key.of( "testFile" ), testBinaryFile );
		instance.executeSource(
		    """
		                           	<bx:mail
		          			from="jclausen@ortussolutions.com"
		          			to="jclausen@ortussolutions.com"
		          			subject="Mail Test"
		          			server="127.0.0.1"
		          			port="25"
		          			spoolEnable="false"
		          			debug="true"
		          messageIdentifier="messageId"
		          messageVariable="messageVar"
		          		>
		          <bx:mailpart type="text">
		       Hello mail!
		       </bx:mailpart>
		          <bx:mailpart type="html">
		       <h1>Hello mail!</h1>
		       </bx:mailpart>
		    <bx:mailparam file="#testFile#"/>
		          </bx:mail>
		                           """,
		    context, BoxSourceType.BOXTEMPLATE );
		assertTrue( variables.get( messageId ) instanceof String );
		assertTrue( variables.get( messageVar ) instanceof Email );
		Email message = ( Email ) variables.get( messageVar );
		assertTrue( message.getEmailBody() instanceof MimeMultipart );
		assertTrue( ( ( MultiPartEmail ) message ).isBoolHasAttachments() );
		assertEquals( "Hello mail!", message.getEmailBody().getBodyPart( 0 ).getContent().toString().trim() );
		assertEquals( "text/plain;charset=utf-8", message.getEmailBody().getBodyPart( 0 ).getContentType().toString() );
		assertEquals( "<h1>Hello mail!</h1>", message.getEmailBody().getBodyPart( 1 ).getContent().toString().trim() );
		assertEquals( "text/html;charset=utf-8", message.getEmailBody().getBodyPart( 1 ).getContentType().toString() );
		assertEquals( "Mail Test", StringCaster.cast( message.getSubject() ).trim() );
		assertEquals( "jclausen@ortussolutions.com", message.getToAddresses().get( 0 ).toString() );
		assertEquals( "jclausen@ortussolutions.com", message.getFromAddress().toString() );
	}

	@DisplayName( "It can test a basic sending of mail with an inline attachment" )
	@Test
	public void testMultiPartWithInline() throws MessagingException, IOException {
		variables.put( Key.of( "testFile" ), testBinaryFile );
		instance.executeSource(
		    """
		                           	<bx:mail
		          			from="jclausen@ortussolutions.com"
		          			to="jclausen@ortussolutions.com"
		          			subject="Mail Test"
		          			server="127.0.0.1"
		          			port="25"
		          			spoolEnable="false"
		          			debug="true"
		          messageIdentifier="messageId"
		          messageVariable="messageVar"
		          		>
		          <bx:mailpart type="text">
		       Hello mail!
		       </bx:mailpart>
		          <bx:mailpart type="html">
		       <h1>Hello mail!</h1>
		       </bx:mailpart>
		    <bx:mailparam file="#testFile#" fileName="foo.jpg" disposition="inline"/>
		          </bx:mail>
		                           """,
		    context, BoxSourceType.BOXTEMPLATE );
		assertTrue( variables.get( messageId ) instanceof String );
		assertTrue( variables.get( messageVar ) instanceof Email );
		Email message = ( Email ) variables.get( messageVar );
		assertTrue( message.getEmailBody() instanceof MimeMultipart );
		assertEquals( "Hello mail!", message.getEmailBody().getBodyPart( 0 ).getContent().toString().trim() );
		assertEquals( "text/plain;charset=utf-8", message.getEmailBody().getBodyPart( 0 ).getContentType().toString() );
		assertEquals( "<h1>Hello mail!</h1>", message.getEmailBody().getBodyPart( 1 ).getContent().toString().trim() );
		assertEquals( "text/html;charset=utf-8", message.getEmailBody().getBodyPart( 1 ).getContentType().toString() );
		assertEquals( "image/jpeg; name=foo.jpg", message.getEmailBody().getBodyPart( 2 ).getContentType().toString() );
		assertEquals( "Mail Test", StringCaster.cast( message.getSubject() ).trim() );
		assertEquals( "jclausen@ortussolutions.com", message.getToAddresses().get( 0 ).toString() );
		assertEquals( "jclausen@ortussolutions.com", message.getFromAddress().toString() );
	}

}
