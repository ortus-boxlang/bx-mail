/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ortus.boxlang.modules.mail.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.apache.commons.mail2.core.EmailException;
import org.apache.commons.mail2.jakarta.Email;
import org.apache.commons.mail2.jakarta.EmailAttachment;
import org.apache.commons.mail2.jakarta.HtmlEmail;
import org.apache.commons.mail2.jakarta.MultiPartEmail;
import org.apache.commons.mail2.jakarta.SimpleEmail;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.mail.Session;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * Unit tests for MailUtil serialization/deserialization functionality
 */
public class MailUtilTest {

	static BoxRuntime		runtime;

	// Test file paths for attachments
	private static String	testAttachmentTxt;
	private static String	testAttachmentImg;

	@BeforeAll
	public static void setUp() {
		runtime = BoxRuntime.getInstance( true );

		try {
			// Create test attachment files
			createTestAttachments();
		} catch ( Exception e ) {
			throw new RuntimeException( "Failed to setup test files", e );
		}
	}

	@AfterAll
	public static void tearDown() {
		// Clean up test files
		try {
			if ( testAttachmentTxt != null )
				Files.deleteIfExists( Path.of( testAttachmentTxt ) );
			if ( testAttachmentImg != null )
				Files.deleteIfExists( Path.of( testAttachmentImg ) );
		} catch ( IOException e ) {
			// Ignore cleanup errors
		}
	}

	private static void createTestAttachments() throws IOException {
		// Create a temporary text file
		Path tempDir = Files.createTempDirectory( "mail-test" );

		testAttachmentTxt = tempDir.resolve( "test-document.txt" ).toString();
		Files.write( Path.of( testAttachmentTxt ),
		    "This is a test attachment document.\nIt contains some sample text for testing email attachments.".getBytes() );

		// Create a small binary file to simulate an image attachment
		testAttachmentImg = tempDir.resolve( "test-image.jpg" ).toString();
		byte[] fakeImageData = new byte[ 1024 ]; // 1KB fake image data
		for ( int i = 0; i < fakeImageData.length; i++ ) {
			fakeImageData[ i ] = ( byte ) ( i % 256 );
		}
		Files.write( Path.of( testAttachmentImg ), fakeImageData );
	}

	@DisplayName( "It can serialize and deserialize a simple email message" )
	@Test
	public void testSimpleEmailSerialization() throws EmailException {
		// Create a simple email
		SimpleEmail originalEmail = new SimpleEmail();
		originalEmail.setFrom( "sender@example.com", "John Sender" );
		originalEmail.addTo( "recipient@example.com", "Jane Recipient" );
		originalEmail.addCc( "cc@example.com", "CC User" );
		originalEmail.addBcc( "bcc@example.com", "BCC User" );
		originalEmail.addReplyTo( "reply@example.com", "Reply User" );
		originalEmail.setSubject( "Test Subject" );
		originalEmail.setMsg( "This is a test message" );
		originalEmail.addHeader( "X-Custom-Header", "Custom Value" );
		originalEmail.setDebug( true );

		// Create attributes
		IStruct	attributes		= Struct.of(
		    Key.charset, "UTF-8"
		);

		// Serialize
		IStruct	serializedData	= MailUtil.emailToSerializableStruct( originalEmail, attributes );

		// Verify serialized data
		assertNotNull( serializedData );
		assertEquals( "Test Subject", serializedData.getAsString( MailKeys.subject ) );
		assertEquals( "This is a test message", serializedData.get( Key.content ) );
		assertEquals( "simple", serializedData.getAsString( MailKeys.emailType ) );
		assertTrue( serializedData.getAsBoolean( MailKeys.debug ) );

		// Check addresses
		IStruct fromAddr = serializedData.getAsStruct( MailKeys.fromAddress );
		assertEquals( "sender@example.com", fromAddr.getAsString( Key.email ) );
		assertEquals( "John Sender", fromAddr.getAsString( Key._NAME ) );

		assertEquals( 1, serializedData.getAsArray( MailKeys.toAddresses ).size() );
		assertEquals( 1, serializedData.getAsArray( MailKeys.ccAddresses ).size() );
		assertEquals( 1, serializedData.getAsArray( MailKeys.bccAddresses ).size() );
		assertEquals( 1, serializedData.getAsArray( MailKeys.replyToAddresses ).size() );

		// Deserialize
		Email deserializedEmail = MailUtil.emailFromSerializableStruct( serializedData );

		// Verify deserialized email
		assertNotNull( deserializedEmail );
		assertTrue( deserializedEmail instanceof SimpleEmail );
		assertEquals( "Test Subject", deserializedEmail.getSubject() );
		assertEquals( "This is a test message", deserializedEmail.getContent() );
		assertTrue( deserializedEmail.isDebug() );

		// Check addresses
		assertEquals( "sender@example.com", deserializedEmail.getFromAddress().getAddress() );
		assertEquals( "John Sender", deserializedEmail.getFromAddress().getPersonal() );
		assertEquals( 1, deserializedEmail.getToAddresses().size() );
		assertEquals( "recipient@example.com", deserializedEmail.getToAddresses().get( 0 ).getAddress() );
		assertEquals( "Jane Recipient", deserializedEmail.getToAddresses().get( 0 ).getPersonal() );

		assertEquals( 1, deserializedEmail.getCcAddresses().size() );
		assertEquals( "cc@example.com", deserializedEmail.getCcAddresses().get( 0 ).getAddress() );

		assertEquals( 1, deserializedEmail.getBccAddresses().size() );
		assertEquals( "bcc@example.com", deserializedEmail.getBccAddresses().get( 0 ).getAddress() );

		assertEquals( 1, deserializedEmail.getReplyToAddresses().size() );
		assertEquals( "reply@example.com", deserializedEmail.getReplyToAddresses().get( 0 ).getAddress() );

		// Check headers
		assertTrue( deserializedEmail.getHeaders().containsKey( "X-Custom-Header" ) );
		assertEquals( "Custom Value", deserializedEmail.getHeaders().get( "X-Custom-Header" ) );
	}

	@DisplayName( "It can serialize and deserialize a multipart email message" )
	@Test
	public void testMultiPartEmailSerialization() throws EmailException {
		// Create a multipart email
		MultiPartEmail originalEmail = new MultiPartEmail();
		originalEmail.setFrom( "sender@example.com", "John Sender" );
		originalEmail.addTo( "recipient@example.com", "Jane Recipient" );
		originalEmail.setSubject( "Multipart Test Subject" );
		originalEmail.setMsg( "This is the text part of the multipart message" );
		originalEmail.addHeader( "X-Priority", "1" );
		originalEmail.setDebug( false );

		// Create attributes
		IStruct	attributes		= Struct.of(
		    Key.charset, "ISO-8859-1"
		);

		// Serialize
		IStruct	serializedData	= MailUtil.emailToSerializableStruct( originalEmail, attributes );

		// Verify serialized data
		assertNotNull( serializedData );
		assertEquals( "Multipart Test Subject", serializedData.getAsString( MailKeys.subject ) );
		assertEquals( "multipart", serializedData.getAsString( MailKeys.emailType ) );
		assertFalse( serializedData.getAsBoolean( MailKeys.debug ) );

		// Deserialize
		Email deserializedEmail = MailUtil.emailFromSerializableStruct( serializedData );

		// Verify deserialized email
		assertNotNull( deserializedEmail );
		assertTrue( deserializedEmail instanceof MultiPartEmail );
		assertEquals( "Multipart Test Subject", deserializedEmail.getSubject() );
		assertFalse( deserializedEmail.isDebug() );

		// Check addresses
		assertEquals( "sender@example.com", deserializedEmail.getFromAddress().getAddress() );
		assertEquals( 1, deserializedEmail.getToAddresses().size() );
		assertEquals( "recipient@example.com", deserializedEmail.getToAddresses().get( 0 ).getAddress() );

		// Check headers
		assertTrue( deserializedEmail.getHeaders().containsKey( "X-Priority" ) );
		assertEquals( "1", deserializedEmail.getHeaders().get( "X-Priority" ) );
	}

	@DisplayName( "It can handle emails with multiple recipients" )
	@Test
	public void testMultipleRecipientsSerialization() throws EmailException {
		// Create email with multiple recipients
		SimpleEmail originalEmail = new SimpleEmail();
		originalEmail.setFrom( "sender@example.com" );
		originalEmail.addTo( "to1@example.com", "Recipient 1" );
		originalEmail.addTo( "to2@example.com", "Recipient 2" );
		originalEmail.addTo( "to3@example.com" ); // No personal name
		originalEmail.addCc( "cc1@example.com", "CC 1" );
		originalEmail.addCc( "cc2@example.com" );
		originalEmail.addBcc( "bcc1@example.com" );
		originalEmail.addBcc( "bcc2@example.com", "BCC 2" );
		originalEmail.setSubject( "Multiple Recipients Test" );
		originalEmail.setMsg( "Test message" );

		IStruct	attributes			= Struct.of( Key.charset, "UTF-8" );

		// Serialize and deserialize
		IStruct	serializedData		= MailUtil.emailToSerializableStruct( originalEmail, attributes );
		Email	deserializedEmail	= MailUtil.emailFromSerializableStruct( serializedData );

		// Verify all recipients are preserved
		assertEquals( 3, deserializedEmail.getToAddresses().size() );
		assertEquals( 2, deserializedEmail.getCcAddresses().size() );
		assertEquals( 2, deserializedEmail.getBccAddresses().size() );

		// Check specific recipients
		assertEquals( "to1@example.com", deserializedEmail.getToAddresses().get( 0 ).getAddress() );
		assertEquals( "Recipient 1", deserializedEmail.getToAddresses().get( 0 ).getPersonal() );
		assertEquals( "to3@example.com", deserializedEmail.getToAddresses().get( 2 ).getAddress() );
		assertEquals( null, deserializedEmail.getToAddresses().get( 2 ).getPersonal() );

		assertEquals( "cc1@example.com", deserializedEmail.getCcAddresses().get( 0 ).getAddress() );
		assertEquals( "CC 1", deserializedEmail.getCcAddresses().get( 0 ).getPersonal() );

		assertEquals( "bcc2@example.com", deserializedEmail.getBccAddresses().get( 1 ).getAddress() );
		assertEquals( "BCC 2", deserializedEmail.getBccAddresses().get( 1 ).getPersonal() );
	}

	@DisplayName( "It can handle emails with bounce address" )
	@Test
	public void testBounceAddressSerialization() throws EmailException {
		SimpleEmail originalEmail = new SimpleEmail();
		originalEmail.setFrom( "sender@example.com" );
		originalEmail.addTo( "recipient@example.com" );
		originalEmail.setBounceAddress( "bounce@example.com" );
		originalEmail.setSubject( "Bounce Test" );
		originalEmail.setMsg( "Test message with bounce address" );

		IStruct	attributes			= Struct.of( Key.charset, "UTF-8" );

		// Serialize and deserialize
		IStruct	serializedData		= MailUtil.emailToSerializableStruct( originalEmail, attributes );
		Email	deserializedEmail	= MailUtil.emailFromSerializableStruct( serializedData );

		// Verify bounce address is preserved
		assertEquals( "bounce@example.com", deserializedEmail.getBounceAddress() );
	}

	@DisplayName( "It can handle emails with multiple headers" )
	@Test
	public void testMultipleHeadersSerialization() throws EmailException {
		SimpleEmail originalEmail = new SimpleEmail();
		originalEmail.setFrom( "sender@example.com" );
		originalEmail.addTo( "recipient@example.com" );
		originalEmail.setSubject( "Headers Test" );
		originalEmail.setMsg( "Test message with headers" );

		// Add multiple headers
		originalEmail.addHeader( "X-Priority", "1" );
		originalEmail.addHeader( "X-Mailer", "BoxLang Mail Module" );
		originalEmail.addHeader( "X-Custom-ID", "12345" );
		originalEmail.addHeader( "List-Unsubscribe", "<mailto:unsubscribe@example.com>" );

		IStruct	attributes			= Struct.of( Key.charset, "UTF-8" );

		// Serialize and deserialize
		IStruct	serializedData		= MailUtil.emailToSerializableStruct( originalEmail, attributes );
		Email	deserializedEmail	= MailUtil.emailFromSerializableStruct( serializedData );

		// Verify all headers are preserved
		assertEquals( "1", deserializedEmail.getHeaders().get( "X-Priority" ) );
		assertEquals( "BoxLang Mail Module", deserializedEmail.getHeaders().get( "X-Mailer" ) );
		assertEquals( "12345", deserializedEmail.getHeaders().get( "X-Custom-ID" ) );
		assertEquals( "<mailto:unsubscribe@example.com>", deserializedEmail.getHeaders().get( "List-Unsubscribe" ) );
	}

	@DisplayName( "It can handle minimal email with only required fields" )
	@Test
	public void testMinimalEmailSerialization() throws EmailException {
		SimpleEmail originalEmail = new SimpleEmail();
		originalEmail.setFrom( "sender@example.com" );
		originalEmail.addTo( "recipient@example.com" );
		originalEmail.setSubject( "Minimal Test" );
		originalEmail.setMsg( "Minimal message" );

		IStruct	attributes			= Struct.of( Key.charset, "UTF-8" );

		// Serialize and deserialize
		IStruct	serializedData		= MailUtil.emailToSerializableStruct( originalEmail, attributes );
		Email	deserializedEmail	= MailUtil.emailFromSerializableStruct( serializedData );

		// Verify minimal fields are preserved
		assertEquals( "sender@example.com", deserializedEmail.getFromAddress().getAddress() );
		assertEquals( "recipient@example.com", deserializedEmail.getToAddresses().get( 0 ).getAddress() );
		assertEquals( "Minimal Test", deserializedEmail.getSubject() );
		assertEquals( "Minimal message", deserializedEmail.getContent() );

		// Verify optional fields are empty/null
		assertEquals( 0, deserializedEmail.getCcAddresses().size() );
		assertEquals( 0, deserializedEmail.getBccAddresses().size() );
		assertEquals( 0, deserializedEmail.getReplyToAddresses().size() );
		assertEquals( null, deserializedEmail.getBounceAddress() );
	}

	@DisplayName( "It preserves charset information" )
	@Test
	public void testCharsetPreservation() throws EmailException {
		SimpleEmail originalEmail = new SimpleEmail();
		originalEmail.setFrom( "sender@example.com" );
		originalEmail.addTo( "recipient@example.com" );
		originalEmail.setSubject( "Charset Test" );
		originalEmail.setMsg( "Message with special chars: áéíóú" );
		originalEmail.setCharset( "ISO-8859-1" );

		IStruct	attributes		= Struct.of( Key.charset, "ISO-8859-1" );

		// Serialize and deserialize
		IStruct	serializedData	= MailUtil.emailToSerializableStruct( originalEmail, attributes );

		// Verify charset is preserved in attributes
		assertEquals( "ISO-8859-1", serializedData.getAsString( Key.charset ) );
		// Note: Email class doesn't have getCharset() method, but charset is applied during send
	}

	@DisplayName( "It can serialize and deserialize a multipart email with HTML and text content" )
	@Test
	public void testMultiPartEmailWithHtmlAndTextSerialization() throws EmailException {
		// Create an HTML email which extends MultiPartEmail
		HtmlEmail originalEmail = new HtmlEmail();
		originalEmail.setFrom( "sender@example.com", "John Sender" );
		originalEmail.addTo( "recipient@example.com", "Jane Recipient" );
		originalEmail.addCc( "cc@example.com" );
		originalEmail.setSubject( "HTML MultiPart Test" );
		originalEmail.setMsg( "This is the plain text part" );
		originalEmail.setHtmlMsg( "<h1>This is the HTML part</h1><p>With <em>formatting</em>!</p>" );
		originalEmail.addHeader( "X-Mailer", "BoxLang Test Suite" );
		originalEmail.addHeader( "X-Priority", "3" );

		IStruct	attributes		= Struct.of( Key.charset, "UTF-8" );

		// Serialize
		IStruct	serializedData	= MailUtil.emailToSerializableStruct( originalEmail, attributes );

		// Verify serialized data
		assertNotNull( serializedData );
		assertEquals( "HTML MultiPart Test", serializedData.getAsString( MailKeys.subject ) );
		assertEquals( "multipart", serializedData.getAsString( MailKeys.emailType ) );

		// Deserialize
		Email deserializedEmail = MailUtil.emailFromSerializableStruct( serializedData );

		// Verify deserialized email
		assertNotNull( deserializedEmail );
		assertTrue( deserializedEmail instanceof MultiPartEmail );
		assertEquals( "HTML MultiPart Test", deserializedEmail.getSubject() );

		// Check addresses
		assertEquals( "sender@example.com", deserializedEmail.getFromAddress().getAddress() );
		assertEquals( "John Sender", deserializedEmail.getFromAddress().getPersonal() );
		assertEquals( 1, deserializedEmail.getToAddresses().size() );
		assertEquals( 1, deserializedEmail.getCcAddresses().size() );

		// Check headers
		assertTrue( deserializedEmail.getHeaders().containsKey( "X-Mailer" ) );
		assertEquals( "BoxLang Test Suite", deserializedEmail.getHeaders().get( "X-Mailer" ) );
		assertEquals( "3", deserializedEmail.getHeaders().get( "X-Priority" ) );
	}

	@DisplayName( "It can serialize and deserialize a multipart email with attachments simulation" )
	@Test
	public void testMultiPartEmailWithAttachmentsSerialization() throws EmailException {
		// Create a multipart email with simulated attachment data
		MultiPartEmail originalEmail = new MultiPartEmail();
		originalEmail.setFrom( "sender@example.com" );
		originalEmail.addTo( "recipient@example.com" );
		originalEmail.setSubject( "Email with Attachments" );
		originalEmail.setMsg( "Please find the attachments" );

		// Add custom headers that would typically be set when attachments are added
		originalEmail.addHeader( "X-Attachment-Count", "2" );
		originalEmail.addHeader( "X-Attachment-Names", "document.pdf,image.jpg" );
		originalEmail.addHeader( "Content-Type", "multipart/mixed" );

		IStruct	attributes		= Struct.of( Key.charset, "UTF-8" );

		// Serialize
		IStruct	serializedData	= MailUtil.emailToSerializableStruct( originalEmail, attributes );

		// Verify serialized data contains attachment info
		assertEquals( "Email with Attachments", serializedData.getAsString( MailKeys.subject ) );
		assertEquals( "multipart", serializedData.getAsString( MailKeys.emailType ) );

		// Deserialize
		Email deserializedEmail = MailUtil.emailFromSerializableStruct( serializedData );

		// Verify attachment-related headers are preserved
		assertTrue( deserializedEmail.getHeaders().containsKey( "X-Attachment-Count" ) );
		assertEquals( "2", deserializedEmail.getHeaders().get( "X-Attachment-Count" ) );
		assertTrue( deserializedEmail.getHeaders().containsKey( "X-Attachment-Names" ) );
		assertEquals( "document.pdf,image.jpg", deserializedEmail.getHeaders().get( "X-Attachment-Names" ) );
	}

	@DisplayName( "It can serialize and deserialize a complex multipart email with all features" )
	@Test
	public void testComplexMultiPartEmailSerialization() throws EmailException {
		// Create a complex multipart email with all possible features
		MultiPartEmail originalEmail = new MultiPartEmail();

		// Set basic properties
		originalEmail.setFrom( "complex.sender@example.com", "Complex Sender" );
		originalEmail.addTo( "primary@example.com", "Primary Recipient" );
		originalEmail.addTo( "secondary@example.com" );
		originalEmail.addCc( "cc1@example.com", "CC One" );
		originalEmail.addCc( "cc2@example.com" );
		originalEmail.addBcc( "bcc@example.com" );
		originalEmail.addReplyTo( "reply@example.com", "Reply Handler" );
		originalEmail.setBounceAddress( "bounce@example.com" );

		// Set content
		originalEmail.setSubject( "Complex MultiPart Email Test" );
		originalEmail.setMsg( "This is a complex multipart message with multiple recipients and features" );
		// Note: For complex MultiPartEmail, HTML content would be added via attachments or parts, not setHtmlMsg

		// Add multiple headers
		originalEmail.addHeader( "X-Priority", "1" );
		originalEmail.addHeader( "X-Mailer", "BoxLang Advanced" );
		originalEmail.addHeader( "X-Message-ID", "complex-test-12345" );
		originalEmail.addHeader( "Reply-To", "custom-reply@example.com" );
		originalEmail.addHeader( "Return-Path", "return@example.com" );

		// Set debug mode
		originalEmail.setDebug( true );

		IStruct	attributes		= Struct.of( Key.charset, "UTF-8" );

		// Serialize
		IStruct	serializedData	= MailUtil.emailToSerializableStruct( originalEmail, attributes );

		// Verify comprehensive serialized data
		assertNotNull( serializedData );
		assertEquals( "Complex MultiPart Email Test", serializedData.getAsString( MailKeys.subject ) );
		assertEquals( "multipart", serializedData.getAsString( MailKeys.emailType ) );
		assertTrue( serializedData.getAsBoolean( MailKeys.debug ) );

		// Check bounce address
		assertEquals( "bounce@example.com", serializedData.getAsString( MailKeys.bounceAddress ) );

		// Check address arrays
		assertEquals( 2, serializedData.getAsArray( MailKeys.toAddresses ).size() );
		assertEquals( 2, serializedData.getAsArray( MailKeys.ccAddresses ).size() );
		assertEquals( 1, serializedData.getAsArray( MailKeys.bccAddresses ).size() );
		assertEquals( 1, serializedData.getAsArray( MailKeys.replyToAddresses ).size() );

		// Deserialize
		Email deserializedEmail = MailUtil.emailFromSerializableStruct( serializedData );

		// Verify comprehensive deserialized email
		assertNotNull( deserializedEmail );
		assertTrue( deserializedEmail instanceof MultiPartEmail );
		assertEquals( "Complex MultiPart Email Test", deserializedEmail.getSubject() );
		assertTrue( deserializedEmail.isDebug() );

		// Check all address types
		assertEquals( 2, deserializedEmail.getToAddresses().size() );
		assertEquals( "primary@example.com", deserializedEmail.getToAddresses().get( 0 ).getAddress() );
		assertEquals( "Primary Recipient", deserializedEmail.getToAddresses().get( 0 ).getPersonal() );
		assertEquals( "secondary@example.com", deserializedEmail.getToAddresses().get( 1 ).getAddress() );

		assertEquals( 2, deserializedEmail.getCcAddresses().size() );
		assertEquals( "cc1@example.com", deserializedEmail.getCcAddresses().get( 0 ).getAddress() );
		assertEquals( "CC One", deserializedEmail.getCcAddresses().get( 0 ).getPersonal() );

		assertEquals( 1, deserializedEmail.getBccAddresses().size() );
		assertEquals( "bcc@example.com", deserializedEmail.getBccAddresses().get( 0 ).getAddress() );

		assertEquals( 1, deserializedEmail.getReplyToAddresses().size() );
		assertEquals( "reply@example.com", deserializedEmail.getReplyToAddresses().get( 0 ).getAddress() );
		assertEquals( "Reply Handler", deserializedEmail.getReplyToAddresses().get( 0 ).getPersonal() );

		// Check all headers are preserved
		assertTrue( deserializedEmail.getHeaders().containsKey( "X-Priority" ) );
		assertEquals( "1", deserializedEmail.getHeaders().get( "X-Priority" ) );
		assertTrue( deserializedEmail.getHeaders().containsKey( "X-Mailer" ) );
		assertEquals( "BoxLang Advanced", deserializedEmail.getHeaders().get( "X-Mailer" ) );
		assertTrue( deserializedEmail.getHeaders().containsKey( "X-Message-ID" ) );
		assertEquals( "complex-test-12345", deserializedEmail.getHeaders().get( "X-Message-ID" ) );
	}

	@DisplayName( "It can serialize and deserialize multipart email with international characters" )
	@Test
	public void testMultiPartEmailInternationalCharactersSerialization() throws EmailException {
		// Create HTML email with international content (HtmlEmail extends MultiPartEmail)
		HtmlEmail originalEmail = new HtmlEmail();
		originalEmail.setFrom( "sender@测试.com", "José Martínez" );
		originalEmail.addTo( "recipient@例え.jp", "田中太郎" );
		originalEmail.setSubject( "国际化测试 - Prueba de Internacionalización - テスト" );
		originalEmail.setMsg( "Plain text: Héllo Wörld! 你好世界! こんにちは世界!" );
		originalEmail.setHtmlMsg( "<p>HTML: <strong>Héllo Wörld!</strong> <em>你好世界!</em> こんにちは世界!</p>" );
		originalEmail.addHeader( "X-Language", "多语言" );

		IStruct	attributes		= Struct.of( Key.charset, "UTF-8" );

		// Serialize
		IStruct	serializedData	= MailUtil.emailToSerializableStruct( originalEmail, attributes );

		// Verify international characters are preserved in serialization
		assertEquals( "国际化测试 - Prueba de Internacionalización - テスト", serializedData.getAsString( MailKeys.subject ) );
		assertEquals( "multipart", serializedData.getAsString( MailKeys.emailType ) );

		// Deserialize
		Email deserializedEmail = MailUtil.emailFromSerializableStruct( serializedData );

		// Verify international characters are preserved after deserialization
		assertEquals( "国际化测试 - Prueba de Internacionalización - テスト", deserializedEmail.getSubject() );
		// International domain names are converted to punycode (ASCII-compatible encoding)
		assertEquals( "sender@xn--0zwm56d.com", deserializedEmail.getFromAddress().getAddress() );
		assertEquals( "José Martínez", deserializedEmail.getFromAddress().getPersonal() );
		assertEquals( "recipient@xn--r8jz45g.jp", deserializedEmail.getToAddresses().get( 0 ).getAddress() );
		assertEquals( "田中太郎", deserializedEmail.getToAddresses().get( 0 ).getPersonal() );

		// Check international header
		assertTrue( deserializedEmail.getHeaders().containsKey( "X-Language" ) );
		assertEquals( "多语言", deserializedEmail.getHeaders().get( "X-Language" ) );
	}

	@DisplayName( "It can serialize and deserialize multipart email with empty and null values" )
	@Test
	public void testMultiPartEmailWithNullValuesSerialization() throws EmailException {
		// Create multipart email with minimal data and some null values
		MultiPartEmail originalEmail = new MultiPartEmail();
		originalEmail.setFrom( "sender@example.com" ); // No personal name
		originalEmail.addTo( "recipient@example.com" ); // No personal name
		originalEmail.setSubject( "" ); // Empty subject
		originalEmail.setMsg( "Minimal message content" ); // MultiPartEmail requires non-empty message

		// Add header with empty value (Apache Commons Mail doesn't allow truly empty header values)
		originalEmail.addHeader( "X-Test-Header", "non-empty" );
		originalEmail.addHeader( "X-Minimal-Header", "minimal" );

		IStruct	attributes		= Struct.of( Key.charset, "UTF-8" );

		// Serialize
		IStruct	serializedData	= MailUtil.emailToSerializableStruct( originalEmail, attributes );

		// Verify empty/null values are handled correctly
		assertEquals( "", serializedData.getAsString( MailKeys.subject ) );
		assertEquals( "multipart", serializedData.getAsString( MailKeys.emailType ) );

		// Deserialize
		Email deserializedEmail = MailUtil.emailFromSerializableStruct( serializedData );

		// Verify empty values are preserved
		assertEquals( "", deserializedEmail.getSubject() );
		assertEquals( "sender@example.com", deserializedEmail.getFromAddress().getAddress() );
		assertEquals( null, deserializedEmail.getFromAddress().getPersonal() );
		assertEquals( "recipient@example.com", deserializedEmail.getToAddresses().get( 0 ).getAddress() );
		assertEquals( null, deserializedEmail.getToAddresses().get( 0 ).getPersonal() );

		// Check headers (Apache Commons Mail doesn't support truly empty header values)
		assertTrue( deserializedEmail.getHeaders().containsKey( "X-Test-Header" ) );
		assertEquals( "non-empty", deserializedEmail.getHeaders().get( "X-Test-Header" ) );
		assertEquals( "minimal", deserializedEmail.getHeaders().get( "X-Minimal-Header" ) );
	}

	@Test
	public void testMultiPartEmailWithRealAttachments() throws Exception {
		// Create a test file attachment
		File			tempFile	= createTestAttachment( "test-document.txt", "This is a test document for email attachment testing." );

		MultiPartEmail	email		= new MultiPartEmail();
		email.setHostName( "localhost" );
		email.setFrom( "sender@test.com" );
		email.addTo( "recipient@test.com" );
		email.setSubject( "MultiPart Email with Real Attachments" );
		email.setMsg( "This email has real file attachments." );

		// Add file attachment
		EmailAttachment attachment1 = new EmailAttachment();
		attachment1.setPath( tempFile.getAbsolutePath() );
		attachment1.setDisposition( EmailAttachment.ATTACHMENT );
		attachment1.setName( "test-document.txt" );
		email.attach( attachment1 );

		// Add inline attachment (note: MultiPartEmail doesn't have embed method, so we'll use attach with INLINE disposition)
		File			imageFile	= createTestAttachment( "test-image.png", "FAKE_PNG_DATA" );
		EmailAttachment	attachment2	= new EmailAttachment();
		attachment2.setPath( imageFile.getAbsolutePath() );
		attachment2.setDisposition( EmailAttachment.INLINE );
		attachment2.setName( "test-image.png" );
		attachment2.setDescription( "Test inline image" );
		email.attach( attachment2 );

		// Serialize
		IStruct	attributes		= new Struct();
		IStruct	serializedData	= MailUtil.emailToSerializableStruct( email, attributes );

		// Verify basic email properties are preserved (attachment serialization not yet implemented)
		assertEquals( "multipart", serializedData.getAsString( MailKeys.emailType ) );
		assertEquals( "MultiPart Email with Real Attachments", serializedData.getAsString( MailKeys.subject ) );
		assertEquals( "sender@test.com", serializedData.getAsStruct( MailKeys.fromAddress ).getAsString( Key.email ) );

		// Note: Attachment serialization would be tested here when implemented
		// For now we just verify the email object structure is preserved

		// Deserialize
		Email deserializedEmail = MailUtil.emailFromSerializableStruct( serializedData );
		assertTrue( deserializedEmail instanceof MultiPartEmail );
		assertEquals( "MultiPart Email with Real Attachments", deserializedEmail.getSubject() );

		// Cleanup
		tempFile.delete();
		imageFile.delete();
	}

	@Test
	public void testMultiPartEmailWithEncryption() throws Exception {
		// Note: This tests the email structure with properties that would be used for encryption
		MultiPartEmail email = new MultiPartEmail();
		email.setHostName( "localhost" );
		email.setFrom( "sender@test.com" );
		email.addTo( "recipient@test.com" );
		email.setSubject( "Encrypted MultiPart Email" );
		email.setMsg( "This is an encrypted email message." );

		// Add encryption properties (for demonstration - actual encryption would happen during send)
		Properties props = new Properties();
		props.setProperty( "mail.smtp.ssl.enable", "true" );
		props.setProperty( "mail.smtp.starttls.enable", "true" );
		props.setProperty( "mail.smime.encrypt", "true" );
		props.setProperty( "mail.smime.keystore.file", "test-keystore.p12" );
		props.setProperty( "mail.smime.keystore.password", "testpass" );

		// Set encryption properties on email
		Session session = Session.getInstance( props );
		email.setMailSession( session );

		// Serialize
		IStruct	attributes		= new Struct();
		IStruct	serializedData	= MailUtil.emailToSerializableStruct( email, attributes );

		// Verify basic email structure is preserved (session properties not yet serialized)
		assertEquals( "multipart", serializedData.getAsString( MailKeys.emailType ) );
		assertEquals( "Encrypted MultiPart Email", serializedData.getAsString( MailKeys.subject ) );
		assertEquals( "sender@test.com", serializedData.getAsStruct( MailKeys.fromAddress ).getAsString( Key.email ) );

		// Deserialize
		Email deserializedEmail = MailUtil.emailFromSerializableStruct( serializedData );

		// Verify basic properties are restored
		assertEquals( "Encrypted MultiPart Email", deserializedEmail.getSubject() );
		assertTrue( deserializedEmail instanceof MultiPartEmail );
	}

	@Test
	public void testMultiPartEmailWithSigning() throws Exception {
		// Note: This tests the email structure with properties that would be used for signing
		MultiPartEmail email = new MultiPartEmail();
		email.setHostName( "localhost" );
		email.setFrom( "signer@test.com" );
		email.addTo( "recipient@test.com" );
		email.setSubject( "Digitally Signed MultiPart Email" );
		email.setMsg( "This email is digitally signed for authenticity." );

		// Add signing properties (for demonstration - actual signing would happen during send)
		Properties props = new Properties();
		props.setProperty( "mail.smime.sign", "true" );
		props.setProperty( "mail.smime.keystore.file", "signing-keystore.p12" );
		props.setProperty( "mail.smime.keystore.password", "signingpass" );
		props.setProperty( "mail.smime.keystore.alias", "signing-cert" );

		// Set signing properties on email
		Session session = Session.getInstance( props );
		email.setMailSession( session );

		// Serialize
		IStruct	attributes		= new Struct();
		IStruct	serializedData	= MailUtil.emailToSerializableStruct( email, attributes );

		// Verify basic email structure is preserved (session properties not yet serialized)
		assertEquals( "multipart", serializedData.getAsString( MailKeys.emailType ) );
		assertEquals( "Digitally Signed MultiPart Email", serializedData.getAsString( MailKeys.subject ) );
		assertEquals( "signer@test.com", serializedData.getAsStruct( MailKeys.fromAddress ).getAsString( Key.email ) );

		// Deserialize
		Email deserializedEmail = MailUtil.emailFromSerializableStruct( serializedData );

		// Verify basic properties are restored
		assertEquals( "Digitally Signed MultiPart Email", deserializedEmail.getSubject() );
		assertTrue( deserializedEmail instanceof MultiPartEmail );
	}

	@Test
	public void testMultiPartEmailWithAttachmentsEncryptionAndSigning() throws Exception {
		// Create comprehensive test demonstrating the structure for all features
		File			attachment	= createTestAttachment( "secure-document.pdf", "CONFIDENTIAL PDF CONTENT" );

		MultiPartEmail	email		= new MultiPartEmail();
		email.setHostName( "localhost" );
		email.setFrom( "secure@test.com" );
		email.addTo( "trusted@test.com" );
		email.setSubject( "Secure MultiPart Email with All Features" );
		email.setMsg( "This email demonstrates all security features: attachments, encryption, and signing." );

		// Add file attachment
		EmailAttachment emailAttachment = new EmailAttachment();
		emailAttachment.setPath( attachment.getAbsolutePath() );
		emailAttachment.setDisposition( EmailAttachment.ATTACHMENT );
		emailAttachment.setName( "secure-document.pdf" );
		emailAttachment.setDescription( "Confidential document" );
		email.attach( emailAttachment );

		// Add comprehensive security properties (for demonstration - actual crypto would happen during send)
		Properties props = new Properties();
		props.setProperty( "mail.smtp.ssl.enable", "true" );
		props.setProperty( "mail.smtp.starttls.enable", "true" );
		props.setProperty( "mail.smime.encrypt", "true" );
		props.setProperty( "mail.smime.sign", "true" );
		props.setProperty( "mail.smime.keystore.file", "comprehensive-keystore.p12" );
		props.setProperty( "mail.smime.keystore.password", "comprehensivepass" );
		props.setProperty( "mail.smime.keystore.alias", "comprehensive-cert" );

		// Set properties on email
		Session session = Session.getInstance( props );
		email.setMailSession( session );

		// Serialize
		IStruct	attributes		= new Struct();
		IStruct	serializedData	= MailUtil.emailToSerializableStruct( email, attributes );

		// Verify basic email structure is preserved
		assertEquals( "multipart", serializedData.getAsString( MailKeys.emailType ) );
		assertEquals( "Secure MultiPart Email with All Features", serializedData.getAsString( MailKeys.subject ) );
		assertEquals( "secure@test.com", serializedData.getAsStruct( MailKeys.fromAddress ).getAsString( Key.email ) );
		assertEquals( "trusted@test.com", ( ( IStruct ) serializedData.getAsArray( MailKeys.toAddresses ).get( 0 ) ).getAsString( Key.email ) );

		// Note: Attachment and session property serialization would be tested here when implemented

		// Deserialize
		Email deserializedEmail = MailUtil.emailFromSerializableStruct( serializedData );
		assertTrue( deserializedEmail instanceof MultiPartEmail );

		// Verify all basic properties are restored
		assertEquals( "Secure MultiPart Email with All Features", deserializedEmail.getSubject() );
		assertEquals( "secure@test.com", deserializedEmail.getFromAddress().getAddress() );
		assertEquals( "trusted@test.com", deserializedEmail.getToAddresses().get( 0 ).getAddress() );

		// Cleanup
		attachment.delete();
	}

	// Helper methods for advanced testing
	private File createTestAttachment( String filename, String content ) throws IOException {
		File	tempDir		= new File( System.getProperty( "java.io.tmpdir" ) );
		File	testFile	= new File( tempDir, filename );

		try ( FileOutputStream fos = new FileOutputStream( testFile ) ) {
			fos.write( content.getBytes( StandardCharsets.UTF_8 ) );
		}

		return testFile;
	}

}