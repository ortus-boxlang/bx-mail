package ortus.boxlang.modules.mail.schedulers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.mail2.jakarta.MultiPartEmail;
import org.apache.commons.mail2.jakarta.SimpleEmail;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ortus.boxlang.BaseIntegrationTest;
import ortus.boxlang.modules.mail.util.MailKeys;
import ortus.boxlang.modules.mail.util.MailUtil;
import ortus.boxlang.runtime.cache.providers.ICacheProvider;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * Integration tests for the SpoolScheduler class
 * Tests the full spooling workflow: enable spooling, create emails, spool them, process them
 */
public class SpoolSchedulerTest extends BaseIntegrationTest {

	private static Path		staticTempDir;
	private SpoolScheduler	scheduler;
	private IStruct			moduleSettings;

	@BeforeAll
	public static void setUpClass() throws IOException {
		// Create a single temp directory for all tests to avoid directory conflicts
		staticTempDir = Files.createTempDirectory( "spoolscheduler-tests" );

		// Create spool and bounce directories
		Files.createDirectories( staticTempDir.resolve( "spool" ) );
		Files.createDirectories( staticTempDir.resolve( "bounce" ) );
	}

	@AfterAll
	public static void tearDownClass() throws IOException {
		// Clean up the static temp directory
		if ( staticTempDir != null && Files.exists( staticTempDir ) ) {
			// Delete the directory and all its contents
			Files.walk( staticTempDir )
			    .sorted( ( a, b ) -> -a.compareTo( b ) ) // Delete files before directories
			    .forEach( path -> {
				    try {
					    Files.delete( path );
				    } catch ( IOException e ) {
					    // Ignore deletion errors during cleanup
				    }
			    } );
		}
	}

	@BeforeEach
	public void setUp() throws IOException {
		// Initialize context
		context		= new ortus.boxlang.runtime.context.ScriptingRequestBoxContext( runtime.getRuntimeContext() );
		variables	= context.getScopeNearby( Key.variables );

		// Use the static directories to avoid conflicts
		Path	spoolDir	= staticTempDir.resolve( "spool" );
		Path	bounceDir	= staticTempDir.resolve( "bounce" );

		// Get and modify module settings to enable spooling and use temp directories
		moduleSettings = moduleService.getModuleSettings( moduleName );

		// Override settings for testing - update the actual module settings
		moduleSettings.put( MailKeys.spoolEnable, true );
		moduleSettings.put( MailKeys.spoolDirectory, spoolDir.toString() );
		moduleSettings.put( MailKeys.bounceDirectory, bounceDir.toString() );
		moduleSettings.put( MailKeys.spoolInterval, "0.1" ); // Process every 100ms for faster testing
		moduleSettings.put( MailKeys.spoolTimeout, 60 );
		moduleSettings.put( MailKeys.bounceTimeout, 60 );

		// Clear any existing caches to ensure clean state
		try {
			if ( runtime.getCacheService().hasCache( MailKeys.mailUnsent ) ) {
				// runtime.getCacheService().getCache( MailKeys.mailUnsent ).clearAll();
			}
			if ( runtime.getCacheService().hasCache( MailKeys.mailBounced ) ) {
				// runtime.getCacheService().getCache( MailKeys.mailBounced ).clearAll();
			}
		} catch ( Exception e ) {
			// Ignore errors during cleanup - caches may not exist yet
		}

		// Initialize scheduler - this will create the caches using our updated module settings
		scheduler = new SpoolScheduler();
		scheduler.configure();

		// Verify caches were created by scheduler
		assertTrue( runtime.getCacheService().hasCache( MailKeys.mailUnsent ), "Unsent mail cache should be created" );
		assertTrue( runtime.getCacheService().hasCache( MailKeys.mailBounced ), "Bounced mail cache should be created" );

		// Verify spool directories exist
		assertTrue( Files.exists( spoolDir ), "Spool directory should exist: " + spoolDir );
		assertTrue( Files.exists( bounceDir ), "Bounce directory should exist: " + bounceDir );
	}

	@AfterEach
	public void tearDown() {
		// Stop scheduler if it was started
		try {
			if ( scheduler != null ) {
				scheduler.shutdown();
			}
		} catch ( Exception e ) {
			// Ignore errors during scheduler shutdown
		}

		// Clean up caches after each test
		try {
			if ( runtime.getCacheService().hasCache( MailKeys.mailUnsent ) ) {
				runtime.getCacheService().getCache( MailKeys.mailUnsent ).clearAll();
			}
			if ( runtime.getCacheService().hasCache( MailKeys.mailBounced ) ) {
				runtime.getCacheService().getCache( MailKeys.mailBounced ).clearAll();
			}
		} catch ( Exception e ) {
			// Ignore errors during cleanup
		}
	}

	@Test
	public void testSpoolEnabledInSettings() {
		// Verify that spooling is enabled in our test setup
		assertTrue( moduleSettings.getAsBoolean( MailKeys.spoolEnable ), "Spooling should be enabled for tests" );
		assertNotNull( moduleSettings.getAsString( MailKeys.spoolDirectory ), "Spool directory should be configured" );
		assertNotNull( moduleSettings.getAsString( MailKeys.bounceDirectory ), "Bounce directory should be configured" );
	}

	@Test
	public void testSimpleEmailSpooling() throws Exception {
		// Create a simple email
		SimpleEmail email = new SimpleEmail();
		email.setFrom( "test@example.com" );
		email.addTo( "recipient@example.com" );
		email.setSubject( "Test Simple Email" );
		email.setMsg( "This is a test message" );

		// Create attributes for spooling
		IStruct			attributes	= Struct.of(
		    MailKeys.spoolEnable, true,
		    Key.from, "test@example.com",
		    Key.to, "recipient@example.com",
		    MailKeys.subject, "Test Simple Email",
		    Key.server, "127.0.0.1",
		    Key.port, 25
		);

		// Get initial cache state
		ICacheProvider	spoolCache	= runtime.getCacheService().getCache( MailKeys.mailUnsent );
		int				initialSize	= spoolCache.getSize();

		// Spool the email
		MailUtil.spoolOrSend( email, attributes, context );

		// Verify email was added to spool cache
		assertEquals( initialSize + 1, spoolCache.getSize(), "Email should be added to spool cache" );

		// Get cache keys and verify structure
		Array cacheKeys = spoolCache.getKeys();
		assertTrue( cacheKeys.size() > initialSize, "Should have at least one spooled message" );

		// Verify the cached email data structure
		String	lastKey		= ( String ) cacheKeys.get( cacheKeys.size() - 1 );
		IStruct	cachedData	= ( IStruct ) spoolCache.get( lastKey ).get();

		assertNotNull( cachedData, "Cached data should not be null" );
		assertTrue( cachedData.containsKey( Key.message ), "Cached data should contain message" );
		assertTrue( cachedData.containsKey( Key.attributes ), "Cached data should contain attributes" );
		assertTrue( cachedData.containsKey( MailKeys.mailServers ), "Cached data should contain mail servers" );
	}

	@Test
	public void testMultiPartEmailSpooling() throws Exception {
		// Create a multipart email with HTML and text content
		MultiPartEmail email = new MultiPartEmail();
		email.setFrom( "test@example.com" );
		email.addTo( "recipient@example.com" );
		email.setSubject( "Test MultiPart Email" );
		email.setMsg( "This is the text part" );
		// Note: MultiPartEmail doesn't have setHtmlMsg, we'll use addPart for HTML content

		// Create attributes for spooling
		IStruct			attributes	= Struct.of(
		    MailKeys.spoolEnable, true,
		    Key.from, "test@example.com",
		    Key.to, "recipient@example.com",
		    MailKeys.subject, "Test MultiPart Email",
		    Key.server, "127.0.0.1",
		    Key.port, 25,
		    Key.type, "html"
		);

		// Get initial cache state
		ICacheProvider	spoolCache	= runtime.getCacheService().getCache( MailKeys.mailUnsent );
		int				initialSize	= spoolCache.getSize();

		// Spool the email
		MailUtil.spoolOrSend( email, attributes, context );

		// Verify email was added to spool cache
		assertEquals( initialSize + 1, spoolCache.getSize(), "MultiPart email should be added to spool cache" );
	}

	@Test
	public void testMultipleEmailsSpooling() throws Exception {
		ICacheProvider	spoolCache	= runtime.getCacheService().getCache( MailKeys.mailUnsent );
		int				initialSize	= spoolCache.getSize();

		// Create and spool multiple emails
		for ( int i = 0; i < 3; i++ ) {
			SimpleEmail email = new SimpleEmail();
			email.setFrom( "test" + i + "@example.com" );
			email.addTo( "recipient" + i + "@example.com" );
			email.setSubject( "Test Email " + i );
			email.setMsg( "This is test message " + i );

			IStruct attributes = Struct.of(
			    MailKeys.spoolEnable, true,
			    Key.from, "test" + i + "@example.com",
			    Key.to, "recipient" + i + "@example.com",
			    MailKeys.subject, "Test Email " + i,
			    Key.server, "127.0.0.1",
			    Key.port, 25
			);

			MailUtil.spoolOrSend( email, attributes, context );
		}

		// Verify all emails were spooled
		assertEquals( initialSize + 3, spoolCache.getSize(), "All 3 emails should be added to spool cache" );
	}

	@Test
	public void testBasicSpooling() throws Exception {
		// Test just the basic spooling functionality
		ICacheProvider	spoolCache	= runtime.getCacheService().getCache( MailKeys.mailUnsent );
		int				initialSize	= spoolCache.getSize();

		// Create and spool a simple email
		SimpleEmail		email		= new SimpleEmail();
		email.setFrom( "sender@example.com" );
		email.addTo( "recipient@example.com" );
		email.setSubject( "Test Spool Email" );
		email.setMsg( "Test message for spooling" );

		IStruct attributes = Struct.of(
		    MailKeys.spoolEnable, true,
		    Key.from, "sender@example.com",
		    Key.to, "recipient@example.com",
		    MailKeys.subject, "Test Spool Email",
		    Key.server, "127.0.0.1",
		    Key.port, 25
		);

		// Spool the email
		MailUtil.spoolOrSend( email, attributes, context );

		// Verify email is in spool
		assertEquals( initialSize + 1, spoolCache.getSize(), "Email should be spooled" );

		// Verify we have at least one key in the cache
		Array cacheKeys = spoolCache.getKeys();
		assertTrue( cacheKeys.size() >= 1, "Should have at least 1 entry in spool cache" );

		// Test cleanup - clear the spool cache for next tests
		spoolCache.clearAll();
		assertEquals( 0, spoolCache.getSize(), "Cache should be empty after clearing" );
	}

	@Test
	public void testSpoolProcessingEmptyQueue() {
		// Ensure spool is empty
		ICacheProvider spoolCache = runtime.getCacheService().getCache( MailKeys.mailUnsent );
		spoolCache.clearAll();

		// Process empty spool
		IStruct result = SpoolScheduler.processSpool();

		// Verify results for empty queue
		assertNotNull( result, "Processing result should not be null" );
		Object	processedObj	= result.get( MailKeys.processed );
		Object	failuresObj		= result.get( MailKeys.failures );
		Object	messagesObj		= result.get( MailKeys.messages );

		int		processed		= processedObj instanceof Integer ? ( Integer ) processedObj : -1;
		int		failures		= failuresObj instanceof Integer ? ( Integer ) failuresObj : -1;
		int		messageCount	= messagesObj instanceof Array ? ( ( Array ) messagesObj ).size() : -1;

		assertEquals( 0, processed, "Should process 0 emails from empty queue" );
		assertEquals( 0, failures, "Should have 0 failures with empty queue" );
		assertEquals( 0, messageCount, "Should have no messages for empty queue" );
	}

	@Test
	public void testCacheInitialization() {
		// Verify that caches were properly initialized by the scheduler
		assertTrue(
		    runtime.getCacheService().hasCache( MailKeys.mailUnsent ),
		    "Unsent mail cache should be created by scheduler"
		);
		assertTrue(
		    runtime.getCacheService().hasCache( MailKeys.mailBounced ),
		    "Bounced mail cache should be created by scheduler"
		);

		// Verify caches are accessible
		ICacheProvider	unsentCache		= runtime.getCacheService().getCache( MailKeys.mailUnsent );
		ICacheProvider	bouncedCache	= runtime.getCacheService().getCache( MailKeys.mailBounced );

		assertNotNull( unsentCache, "Unsent cache should be accessible" );
		assertNotNull( bouncedCache, "Bounced cache should be accessible" );
	}

	@Test
	public void testSpoolDirectoryConfiguration() {
		// Verify that spool directories are properly configured
		String	spoolDir	= moduleSettings.getAsString( MailKeys.spoolDirectory );
		String	bounceDir	= moduleSettings.getAsString( MailKeys.bounceDirectory );

		assertNotNull( spoolDir, "Spool directory should be configured" );
		assertNotNull( bounceDir, "Bounce directory should be configured" );
		assertTrue( Files.exists( Path.of( spoolDir ) ), "Spool directory should exist" );
		assertTrue( Files.exists( Path.of( bounceDir ) ), "Bounce directory should exist" );
	}

	@Test
	public void testProcessSimpleEmailInQueue() throws Exception {
		// Create and spool a simple email
		SimpleEmail email = new SimpleEmail();
		email.setFrom( "test@example.com" );
		email.addTo( "recipient@example.com" );
		email.setSubject( "Test Simple Email Processing" );
		email.setMsg( "This email should be processed from the queue" );

		// Create attributes for spooling
		IStruct			attributes	= Struct.of(
		    MailKeys.spoolEnable, true,
		    MailKeys.spoolDirectory, moduleSettings.getAsString( MailKeys.spoolDirectory ),
		    MailKeys.bounceDirectory, moduleSettings.getAsString( MailKeys.bounceDirectory )
		);

		// Get initial cache state
		ICacheProvider	spoolCache	= runtime.getCacheService().getCache( MailKeys.mailUnsent );
		int				initialSize	= spoolCache.getSize();

		// Spool the email
		MailUtil.spoolOrSend( email, attributes, context );

		// Verify email was added to spool cache
		assertEquals( initialSize + 1, spoolCache.getSize(), "Email should be added to spool cache" );

		// Process the spool queue
		IStruct result = SpoolScheduler.processSpool();

		// Verify processing results
		assertNotNull( result, "Processing result should not be null" );
		assertTrue( result.containsKey( "processed" ), "Result should contain processed count" );

		// Cache should be empty after processing (email was sent)
		assertEquals( initialSize, spoolCache.getSize(), "Cache should be empty after processing" );
	}

	@Test
	public void testProcessMultiPartEmailInQueue() throws Exception {
		// Create a multipart email
		MultiPartEmail email = new MultiPartEmail();
		email.setFrom( "test@example.com" );
		email.addTo( "recipient@example.com" );
		email.setSubject( "Test MultiPart Email Processing" );
		email.setMsg( "This is the text part" );

		// Add HTML content using addPart
		email.addPart( "<h1>HTML Content</h1><p>This is the HTML part</p>", "text/html" );

		// Create attributes for spooling
		IStruct			attributes	= Struct.of(
		    MailKeys.spoolEnable, true,
		    MailKeys.spoolDirectory, moduleSettings.getAsString( MailKeys.spoolDirectory ),
		    MailKeys.bounceDirectory, moduleSettings.getAsString( MailKeys.bounceDirectory )
		);

		// Get initial cache state
		ICacheProvider	spoolCache	= runtime.getCacheService().getCache( MailKeys.mailUnsent );
		int				initialSize	= spoolCache.getSize();

		// Spool the email
		MailUtil.spoolOrSend( email, attributes, context );

		// Verify email was added to spool cache
		assertEquals( initialSize + 1, spoolCache.getSize(), "MultiPart email should be added to spool cache" );

		// Process the spool queue
		IStruct result = SpoolScheduler.processSpool();

		// Verify processing results
		assertNotNull( result, "Processing result should not be null" );
		assertTrue( result.containsKey( "processed" ), "Result should contain processed count" );

		// Cache should be empty after processing (email was sent)
		assertEquals( initialSize, spoolCache.getSize(), "Cache should be empty after processing multipart email" );
	}

	@Test
	public void testProcessMultipleEmailsInQueue() throws Exception {
		ICacheProvider	spoolCache	= runtime.getCacheService().getCache( MailKeys.mailUnsent );
		int				initialSize	= spoolCache.getSize();

		// Create attributes for spooling
		IStruct			attributes	= Struct.of(
		    MailKeys.spoolEnable, true,
		    MailKeys.spoolDirectory, moduleSettings.getAsString( MailKeys.spoolDirectory ),
		    MailKeys.bounceDirectory, moduleSettings.getAsString( MailKeys.bounceDirectory )
		);

		// Create and spool multiple emails of different types
		// Simple email
		SimpleEmail		simpleEmail	= new SimpleEmail();
		simpleEmail.setFrom( "sender1@example.com" );
		simpleEmail.addTo( "recipient1@example.com" );
		simpleEmail.setSubject( "Simple Email in Batch" );
		simpleEmail.setMsg( "This is a simple email in a batch" );
		MailUtil.spoolOrSend( simpleEmail, attributes, context );

		// MultiPart email
		MultiPartEmail multiEmail = new MultiPartEmail();
		multiEmail.setFrom( "sender2@example.com" );
		multiEmail.addTo( "recipient2@example.com" );
		multiEmail.setSubject( "MultiPart Email in Batch" );
		multiEmail.setMsg( "Text content" );
		multiEmail.addPart( "<p>HTML content</p>", "text/html" );
		MailUtil.spoolOrSend( multiEmail, attributes, context );

		// Another simple email
		SimpleEmail simpleEmail2 = new SimpleEmail();
		simpleEmail2.setFrom( "sender3@example.com" );
		simpleEmail2.addTo( "recipient3@example.com" );
		simpleEmail2.setSubject( "Second Simple Email" );
		simpleEmail2.setMsg( "Another simple email" );
		MailUtil.spoolOrSend( simpleEmail2, attributes, context );

		// Verify all emails were added to spool cache
		assertEquals( initialSize + 3, spoolCache.getSize(), "All 3 emails should be added to spool cache" );

		// Process the spool queue
		IStruct result = SpoolScheduler.processSpool();

		// Verify processing results
		assertNotNull( result, "Processing result should not be null" );
		assertTrue( result.containsKey( "processed" ), "Result should contain processed count" );

		// Cache should be empty after processing all emails
		assertEquals( initialSize, spoolCache.getSize(), "Cache should be empty after processing all emails" );
	}

	@Test
	public void testProcessEmailWithAttachment() throws Exception {
		// Create a multipart email with attachment
		MultiPartEmail email = new MultiPartEmail();
		email.setFrom( "test@example.com" );
		email.addTo( "recipient@example.com" );
		email.setSubject( "Test Email with Attachment Processing" );
		email.setMsg( "This email has an attachment" );

		// Create a test file for attachment
		Path testFile = staticTempDir.resolve( "test-attachment.txt" );
		Files.write( testFile, "Test attachment content".getBytes() );

		// Add attachment using attach method
		email.attach( testFile.toFile() );

		// Create attributes for spooling
		IStruct			attributes	= Struct.of(
		    MailKeys.spoolEnable, true,
		    MailKeys.spoolDirectory, moduleSettings.getAsString( MailKeys.spoolDirectory ),
		    MailKeys.bounceDirectory, moduleSettings.getAsString( MailKeys.bounceDirectory )
		);

		// Get initial cache state
		ICacheProvider	spoolCache	= runtime.getCacheService().getCache( MailKeys.mailUnsent );
		int				initialSize	= spoolCache.getSize();

		// Spool the email
		MailUtil.spoolOrSend( email, attributes, context );

		// Verify email was added to spool cache
		assertEquals( initialSize + 1, spoolCache.getSize(), "Email with attachment should be added to spool cache" );

		// Process the spool queue
		IStruct result = SpoolScheduler.processSpool();

		// Verify processing results
		assertNotNull( result, "Processing result should not be null" );
		assertTrue( result.containsKey( "processed" ), "Result should contain processed count" );

		// Cache should be empty after processing (email was sent)
		assertEquals( initialSize, spoolCache.getSize(), "Cache should be empty after processing email with attachment" );
	}

	@Test
	public void testProcessHighPriorityEmail() throws Exception {
		// Create a high-priority simple email
		SimpleEmail email = new SimpleEmail();
		email.setFrom( "urgent@example.com" );
		email.addTo( "recipient@example.com" );
		email.setSubject( "URGENT: High Priority Email" );
		email.setMsg( "This is a high priority email that should be processed" );

		// Set headers for high priority
		email.addHeader( "X-Priority", "1" );
		email.addHeader( "Priority", "urgent" );

		// Create attributes for spooling
		IStruct			attributes	= Struct.of(
		    MailKeys.spoolEnable, true,
		    MailKeys.spoolDirectory, moduleSettings.getAsString( MailKeys.spoolDirectory ),
		    MailKeys.bounceDirectory, moduleSettings.getAsString( MailKeys.bounceDirectory )
		);

		// Get initial cache state
		ICacheProvider	spoolCache	= runtime.getCacheService().getCache( MailKeys.mailUnsent );
		int				initialSize	= spoolCache.getSize();

		// Spool the email
		MailUtil.spoolOrSend( email, attributes, context );

		// Verify email was added to spool cache
		assertEquals( initialSize + 1, spoolCache.getSize(), "High priority email should be added to spool cache" );

		// Process the spool queue
		IStruct result = SpoolScheduler.processSpool();

		// Verify processing results
		assertNotNull( result, "Processing result should not be null" );
		assertTrue( result.containsKey( "processed" ), "Result should contain processed count" );

		// Cache should be empty after processing (email was sent)
		assertEquals( initialSize, spoolCache.getSize(), "Cache should be empty after processing high priority email" );
	}
}