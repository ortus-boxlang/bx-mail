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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.mail2.jakarta.EmailAttachment;
import org.apache.commons.mail2.jakarta.MultiPartEmail;
import org.apache.commons.mail2.jakarta.SimpleEmail;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.mail.util.ByteArrayDataSource;
import ortus.boxlang.BaseIntegrationTest;
import ortus.boxlang.modules.mail.schedulers.SpoolScheduler;
import ortus.boxlang.runtime.events.BaseInterceptor;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * The mail interception points: what is announced, in what order, with what data, and that nothing secret or heavy leaves the module.
 */
public class MailEventsTest extends BaseIntegrationTest {

	/** Records every mail event in the order it was announced. */
	public static class Recorder extends BaseInterceptor {

		final List<String>	names	= new CopyOnWriteArrayList<>();
		final List<IStruct>	data	= new CopyOnWriteArrayList<>();

		private void keep( String name, IStruct d ) {
			names.add( name );
			data.add( d );
		}

		@InterceptionPoint
		public void onMailInvocation( IStruct d ) {
			keep( "onMailInvocation", d );
		}

		@InterceptionPoint
		public void onMailSpooled( IStruct d ) {
			keep( "onMailSpooled", d );
		}

		@InterceptionPoint
		public void onMailSent( IStruct d ) {
			keep( "onMailSent", d );
		}

		@InterceptionPoint
		public void onMailError( IStruct d ) {
			keep( "onMailError", d );
		}

		@InterceptionPoint
		public void onMailBounce( IStruct d ) {
			keep( "onMailBounce", d );
		}

		@InterceptionPoint
		public void onMailSpoolRun( IStruct d ) {
			keep( "onMailSpoolRun", d );
		}

		IStruct first( String name ) {
			int i = names.indexOf( name );
			assertTrue( i >= 0, name + " was not announced, got " + names );
			return data.get( i );
		}
	}

	private static Path	tempDir;
	private Recorder	recorder;
	private IStruct		moduleSettings;

	@BeforeAll
	public static void createFolders() throws IOException {
		tempDir = Files.createTempDirectory( "mail-events-tests" );
		Files.createDirectories( tempDir.resolve( "spool" ) );
		Files.createDirectories( tempDir.resolve( "bounce" ) );
	}

	private static void dropSpoolCaches() {
		for ( Key cache : List.of( MailKeys.mailUnsent, MailKeys.mailBounced ) ) {
			if ( runtime.getCacheService().hasCache( cache ) ) {
				runtime.getCacheService().shutdownCache( cache );
			}
		}
	}

	@AfterAll
	public static void removeFolders() throws IOException {
		dropSpoolCaches();
		try ( var walk = Files.walk( tempDir ) ) {
			walk.sorted( ( a, b ) -> -a.compareTo( b ) ).forEach( p -> p.toFile().delete() );
		}
	}

	@BeforeEach
	public void register() {
		context			= new ortus.boxlang.runtime.context.ScriptingRequestBoxContext( runtime.getRuntimeContext() );
		moduleSettings	= moduleService.getModuleSettings( moduleName );
		moduleSettings.put( MailKeys.spoolDirectory, tempDir.resolve( "spool" ).toString() );
		moduleSettings.put( MailKeys.bounceDirectory, tempDir.resolve( "bounce" ).toString() );
		moduleSettings.put( MailKeys.spoolTimeout, 60 );
		moduleSettings.put( MailKeys.bounceTimeout, 60 );
		// The spool caches point at the folder of whichever test class created them first: start from our own
		dropSpoolCaches();
		runtime.getInterceptorService().registerInterceptionPoint( MailEvents.ON_INVOCATION, MailEvents.ON_SPOOLED,
		    MailEvents.ON_SENT, MailEvents.ON_ERROR, MailEvents.ON_BOUNCE, MailEvents.ON_SPOOL_RUN );
		recorder = new Recorder();
		runtime.getInterceptorService().register( recorder );
		new SpoolScheduler().configure();
	}

	@AfterEach
	public void unregister() {
		runtime.getInterceptorService().unregister( recorder );
		runtime.getCacheService().getCache( MailKeys.mailUnsent ).clearAll();
		runtime.getCacheService().getCache( MailKeys.mailBounced ).clearAll();
	}

	private IStruct attributes( int port, boolean spool ) {
		return Struct.of(
		    MailKeys.spoolEnable, spool,
		    Key.from, "from@example.com",
		    Key.to, "to@example.com",
		    MailKeys.subject, "Hello",
		    Key.server, "127.0.0.1",
		    Key.port, port,
		    Key.username, "smtp-user",
		    Key.password, "top-secret-password"
		);
	}

	private SimpleEmail simple() throws Exception {
		SimpleEmail email = new SimpleEmail();
		email.setFrom( "from@example.com", "Sender" );
		email.addTo( "to@example.com" );
		email.setSubject( "Hello" );
		email.setMsg( "Plain body" );
		return email;
	}

	@Test
	public void aSentMessageAnnouncesInvocationAndSent() throws Exception {
		try ( MockSmtpServer smtp = new MockSmtpServer() ) {
			MailUtil.spoolOrSend( simple(), attributes( smtp.port(), false ), context );
			assertEquals( List.of( "onMailInvocation", "onMailSent" ), recorder.names );
			IStruct	invocation	= recorder.first( "onMailInvocation" );
			IStruct	sent		= recorder.first( "onMailSent" );
			String	id			= invocation.getAsString( Key.of( "id" ) );
			assertNotNull( id );
			assertEquals( id, sent.getAsString( Key.of( "id" ) ) );
			assertFalse( ( Boolean ) invocation.get( Key.of( "spooled" ) ) );
			assertTrue( ( Long ) sent.get( Key.of( "nanos" ) ) >= ( Long ) invocation.get( Key.of( "nanos" ) ) );
			assertNotNull( sent.get( Key.of( "at" ) ) );
			assertTrue( ( Long ) sent.get( Key.of( "durationNanos" ) ) / 1_000_000L == ( ( Number ) sent.get( Key.of( "ms" ) ) ).longValue() );
			assertEquals( 1, sent.get( Key.of( "attempt" ) ) );
			assertFalse( ( Boolean ) sent.get( Key.of( "failover" ) ) );
			IStruct server = sent.getAsStruct( Key.of( "server" ) );
			assertEquals( "127.0.0.1", server.get( Key.of( "host" ) ) );
			assertEquals( smtp.port(), server.get( Key.of( "port" ) ) );
			IStruct summary = invocation.getAsStruct( Key.of( "summary" ) );
			assertEquals( "Hello", summary.get( Key.of( "subject" ) ) );
			assertEquals( "from@example.com", summary.getAsStruct( Key.of( "from" ) ).get( Key.of( "email" ) ) );
			Array parts = summary.getAsArray( Key.of( "parts" ) );
			assertEquals( 1, parts.size() );
			assertEquals( "Plain body", ( ( IStruct ) parts.get( 0 ) ).get( Key.of( "content" ) ) );
		}
	}

	@Test
	public void noPasswordLeavesTheModule() throws Exception {
		try ( MockSmtpServer smtp = new MockSmtpServer() ) {
			MailUtil.spoolOrSend( simple(), attributes( smtp.port(), false ), context );
			for ( IStruct d : recorder.data ) {
				assertFalse( d.toString().contains( "top-secret-password" ), "a payload carries the password: " + d );
			}
		}
	}

	@Test
	public void aFailedPrimaryAnnouncesTheErrorAndTheFailoverSends() throws Exception {
		try ( MockSmtpServer good = new MockSmtpServer() ) {
			int		dead	= unusedPort();
			Array	servers	= Array.of(
			    Struct.of( Key.server, "127.0.0.1", Key.port, dead, Key.username, "u", Key.password, "p", Key.timeout, "2000", MailKeys.SSL, false,
			        MailKeys.TLS, false ),
			    Struct.of( Key.server, "127.0.0.1", Key.port, good.port(), Key.username, "u", Key.password, "p", Key.timeout, "2000", MailKeys.SSL,
			        false, MailKeys.TLS, false )
			);
			MailUtil.sendMessage( servers, Struct.of(), simple(), "fixed-id", false );
			assertEquals( List.of( "onMailError", "onMailSent" ), recorder.names );
			IStruct error = recorder.first( "onMailError" );
			assertEquals( "fixed-id", error.get( Key.of( "id" ) ) );
			assertTrue( ( Boolean ) error.get( Key.of( "willFailover" ) ) );
			assertFalse( error.getAsString( Key.of( "error" ) ).isBlank() );
			IStruct sent = recorder.first( "onMailSent" );
			assertTrue( ( Boolean ) sent.get( Key.of( "failover" ) ) );
			assertEquals( 2, sent.get( Key.of( "attempt" ) ) );
		}
	}

	@Test
	public void aSpooledMessageIsAnnouncedThenSentByTheSpoolRun() throws Exception {
		try ( MockSmtpServer smtp = new MockSmtpServer() ) {
			MailUtil.spoolOrSend( simple(), attributes( smtp.port(), true ), context );
			assertEquals( List.of( "onMailInvocation", "onMailSpooled" ), recorder.names );
			String id = recorder.first( "onMailInvocation" ).getAsString( Key.of( "id" ) );
			assertEquals( id, recorder.first( "onMailSpooled" ).getAsString( Key.of( "id" ) ) );
			assertTrue( ( Integer ) recorder.first( "onMailSpooled" ).get( Key.of( "queueDepth" ) ) >= 1 );
			SpoolScheduler.processSpool();
			assertTrue( recorder.names.contains( "onMailSent" ), recorder.names.toString() );
			IStruct sent = recorder.first( "onMailSent" );
			assertEquals( id, sent.getAsString( Key.of( "id" ) ) );
			assertTrue( ( Boolean ) sent.get( Key.of( "fromSpool" ) ) );
			IStruct run = recorder.first( "onMailSpoolRun" );
			assertEquals( 1, run.get( Key.of( "processed" ) ) );
			assertEquals( 0, run.get( Key.of( "failures" ) ) );
		}
	}

	@Test
	public void aSpooledMessageThatCannotBeSentBounces() throws Exception {
		int dead = unusedPort();
		MailUtil.spoolOrSend( simple(), attributes( dead, true ), context );
		String id = recorder.first( "onMailInvocation" ).getAsString( Key.of( "id" ) );
		SpoolScheduler.processSpool();
		assertTrue( recorder.names.contains( "onMailError" ), recorder.names.toString() );
		IStruct bounce = recorder.first( "onMailBounce" );
		assertEquals( id, bounce.get( Key.of( "id" ) ) );
		assertFalse( bounce.getAsString( Key.of( "error" ) ).isBlank() );
		assertFalse( ( Boolean ) bounce.get( Key.of( "unreadable" ) ) );
		assertEquals( 1, recorder.first( "onMailSpoolRun" ).get( Key.of( "failures" ) ) );
	}

	@Test
	public void theSummaryDescribesPartsInlineImagesAndAttachmentsWithoutTheirBytes() throws Exception {
		MultiPartEmail email = new MultiPartEmail();
		email.setFrom( "from@example.com" );
		email.addTo( "to@example.com" );
		email.setSubject( "Parts" );
		email.setMsg( "Text part" );
		byte[] png = new byte[] { 1, 2, 3, 4, 5 };
		email.attach( new ByteArrayDataSource( png, "image/png" ), "logo.png", "logo", EmailAttachment.INLINE );
		byte[] pdf = new byte[ 1_000_000 ];
		email.attach( new ByteArrayDataSource( pdf, "application/pdf" ), "report.pdf", "report", EmailAttachment.ATTACHMENT );

		IStruct	summary	= MailEvents.summarize( email );
		Array	parts	= summary.getAsArray( Key.of( "parts" ) );
		assertEquals( 3, parts.size() );
		IStruct inlinePart = ( IStruct ) parts.get( 1 );
		assertEquals( "binary", inlinePart.get( Key.of( "type" ) ) );
		assertEquals( 5, inlinePart.get( Key.of( "size" ) ) );
		assertTrue( inlinePart.get( Key.of( "bytes" ) ) instanceof byte[] );
		IStruct attachment = ( IStruct ) parts.get( 2 );
		assertEquals( "report.pdf", attachment.get( Key.of( "fileName" ) ) );
		assertFalse( attachment.containsKey( Key.of( "bytes" ) ), "an attachment must not travel as bytes" );
		assertTrue( ( Integer ) attachment.get( Key.of( "size" ) ) >= 1_000_000 );
	}

	@Test
	public void aListenerThatThrowsNeverBreaksTheMail() throws Exception {
		BaseInterceptor bad = new BaseInterceptor() {

			@InterceptionPoint
			public void onMailSent( IStruct d ) {
				throw new IllegalStateException( "listener failure" );
			}
		};
		runtime.getInterceptorService().register( bad );
		try ( MockSmtpServer smtp = new MockSmtpServer() ) {
			MailUtil.spoolOrSend( simple(), attributes( smtp.port(), false ), context );
			assertEquals( 1, smtp.messages.size() );
		} finally {
			runtime.getInterceptorService().unregister( bad );
		}
	}

	@Test
	public void nothingIsBuiltWhenNobodyListens() throws Exception {
		runtime.getInterceptorService().unregister( recorder );
		boolean[] built = { false };
		MailEvents.announce( Key.of( "onMailSpoolRun" ), () -> {
			built[ 0 ] = true;
			return Struct.of();
		} );
		assertFalse( built[ 0 ], "the payload was built with no listener" );
	}

	private static int unusedPort() throws IOException {
		try ( ServerSocket s = new ServerSocket( 0 ) ) {
			return s.getLocalPort();
		}
	}

	/** A minimal SMTP server that accepts every message. */
	private static class MockSmtpServer implements AutoCloseable {

		private final ServerSocket	serverSocket;
		private final List<String>	messages	= new CopyOnWriteArrayList<>();

		MockSmtpServer() throws IOException {
			this.serverSocket = new ServerSocket( 0 );
			Thread t = new Thread( () -> {
				while ( !serverSocket.isClosed() ) {
					try ( Socket socket = serverSocket.accept() ) {
						handle( socket );
					} catch ( IOException e ) {
						// closed
					}
				}
			}, "mock-smtp-events" );
			t.setDaemon( true );
			t.start();
		}

		int port() {
			return serverSocket.getLocalPort();
		}

		private void handle( Socket socket ) throws IOException {
			socket.setSoTimeout( 15000 );
			BufferedReader	in	= new BufferedReader( new InputStreamReader( socket.getInputStream(), StandardCharsets.UTF_8 ) );
			PrintWriter		out	= new PrintWriter( socket.getOutputStream(), true );
			out.println( "220 mock-smtp ESMTP" );
			String			line;
			boolean			inData	= false;
			StringBuilder	data	= new StringBuilder();
			while ( ( line = in.readLine() ) != null ) {
				if ( inData ) {
					if ( line.equals( "." ) ) {
						inData = false;
						messages.add( data.toString() );
						data = new StringBuilder();
						out.println( "250 OK: queued" );
					} else {
						data.append( line ).append( "\r\n" );
					}
					continue;
				}
				String upper = line.toUpperCase( Locale.ROOT );
				if ( upper.startsWith( "EHLO" ) || upper.startsWith( "HELO" ) ) {
					out.println( "250-mock-smtp" );
					out.println( "250 8BITMIME" );
				} else if ( upper.startsWith( "DATA" ) ) {
					inData = true;
					out.println( "354 End data with <CR><LF>.<CR><LF>" );
				} else if ( upper.startsWith( "QUIT" ) ) {
					out.println( "221 Bye" );
					break;
				} else {
					out.println( "250 OK" );
				}
			}
		}

		@Override
		public void close() throws IOException {
			serverSocket.close();
		}
	}
}
