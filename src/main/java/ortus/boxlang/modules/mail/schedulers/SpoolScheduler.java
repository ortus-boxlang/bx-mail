package ortus.boxlang.modules.mail.schedulers;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.modules.mail.components.Mail;
import ortus.boxlang.modules.mail.util.MailKeys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.async.tasks.BaseScheduler;
import ortus.boxlang.runtime.async.tasks.ScheduledTask;
import ortus.boxlang.runtime.cache.ICacheEntry;
import ortus.boxlang.runtime.cache.providers.ICacheProvider;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.dynamic.casters.DoubleCaster;
import ortus.boxlang.runtime.dynamic.casters.IntegerCaster;
import ortus.boxlang.runtime.dynamic.casters.LongCaster;
import ortus.boxlang.runtime.dynamic.casters.StructCaster;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.util.FileSystemUtil;

public class SpoolScheduler extends BaseScheduler {

	static final Key				spoolCache				= MailKeys.mailUnsent;
	static final Key				bounceCache				= MailKeys.mailBounced;

	static final double				minuteToMilisMulitplier	= 60000d;

	private static final BoxRuntime	runtime					= BoxRuntime.getInstance();
	private static final IStruct	moduleSettings			= runtime.getModuleService().getModuleSettings( MailKeys._MODULE_NAME );

	private static final Boolean	logEnabled				= BooleanCaster.cast( moduleSettings.get( MailKeys.logEnabled ) );
	private static final Logger		logger					= LoggerFactory.getLogger( Mail.class );

	public SpoolScheduler() {
		super( "SpoolScheduler" );
	}

	/**
	 * Declare the tasks for this scheduler
	 */
	@Override
	public void configure() {

		if ( !runtime.getCacheService().hasCache( spoolCache ) ) {
			runtime.getCacheService().createCache(
			    spoolCache,
			    Key.boxCacheProvider,
			    Struct.of(
			        Key.objectStore, MailKeys.fileSystemStore.getName(),
			        Key.directory, moduleSettings.getAsString( MailKeys.spoolDirectory ),
			        Key.defaultTimeout, IntegerCaster.cast( moduleSettings.get( MailKeys.spoolTimeout ) ),
			        Key.useLastAccessTimeouts, false,
			        Key.evictCount, 0
			    )
			);
		}

		if ( !runtime.getCacheService().hasCache( bounceCache ) ) {
			runtime.getCacheService().createCache(
			    bounceCache,
			    Key.boxCacheProvider,
			    Struct.of(
			        Key.objectStore, MailKeys.fileSystemStore.getName(),
			        Key.directory, moduleSettings.getAsString( MailKeys.bounceDirectory ),
			        Key.defaultTimeout, IntegerCaster.cast( moduleSettings.get( MailKeys.bounceTimeout ) ),
			        Key.useLastAccessTimeouts, false,
			        Key.evictCount, 0
			    )
			);
		}

		long spoolIntervalMillis = LongCaster.cast( DoubleCaster.cast( moduleSettings.get( MailKeys.spoolInterval ) ) * minuteToMilisMulitplier );

		task( "SpoolTask" )
		    .call( SpoolScheduler::processSpool )
		    .every( spoolIntervalMillis, TimeUnit.MILLISECONDS )
		    .onFailure( SpoolScheduler::onSpoolFailure )
		    .onSuccess( SpoolScheduler::onSpoolProcessed );

	}

	/**
	 * Processess the spool according to the settings
	 *
	 * @return
	 */
	protected static IStruct processSpool() {
		IStruct			result	= Struct.of(
		    MailKeys.messages, new Array(),
		    MailKeys.processed, 0,
		    MailKeys.failures, 0
		);

		ICacheProvider	cache	= runtime.getCacheService().getCache( spoolCache );
		ICacheProvider	bounced	= runtime.getCacheService().getCache( bounceCache );

		cache.getKeysStream()
		    .map( key -> cache.get( key ) )
		    .forEach( opt -> {
			    ICacheEntry entry = ( ICacheEntry ) opt.get();
			    try {
				    Email message;
				    Boolean deleteAttachments = false;
				    String mimeAttach		= null;
				    if ( entry.value().get() instanceof Email ) {
					    message = ( Email ) entry.value().get();
				    } else {
					    IStruct entryParams = StructCaster.cast( entry.value().get() );
					    message = ( Email ) entryParams.get( Key.message );
					    IStruct entryAttributes = entryParams.getAsStruct( Key.attributes );
					    deleteAttachments = BooleanCaster.cast( entryAttributes.get( MailKeys.remove ) );
					    mimeAttach		= entryAttributes.getAsString( MailKeys.mimeAttach );
				    }
				    message.send();
				    if ( deleteAttachments && mimeAttach != null && FileSystemUtil.exists( mimeAttach ) ) {
					    FileSystemUtil.deleteFile( mimeAttach );
				    }
				    result.put( MailKeys.processed, result.getAsInteger( MailKeys.processed ) + 1 );
				    if ( logEnabled ) {
					    logger.atInfo().log( String.format(
					        "Message [%s] successfully sent",
					        entry.key().getName()
					    ) );
				    }
			    } catch ( EmailException e ) {
				    result.put( MailKeys.failures, result.getAsInteger( MailKeys.failures ) + 1 );
				    result.getAsArray( MailKeys.messages )
				        .push(
				            String.format(
				                "An exception occurred while attempting to send an email with the identifier [%s]: %s, StackTrace: %s",
				                entry.key().getName(),
				                e.getMessage(),
				                e.getStackTrace().toString()
				            )
				        );
				    bounced.set( entry.key().getName(), entry.value().get() );
			    } finally {
				    cache.clear( entry.key().getName() );
			    }
		    } );

		return result;

	}

	protected static void onSpoolProcessed( ScheduledTask task, Optional outcome ) {
		IStruct result = StructCaster.cast( outcome.get() );
		if ( result == null ) {

		}
	}

	protected static void onSpoolFailure( ScheduledTask task, Throwable exception ) {
		logger.debug( "Spool Task failed: " + exception.getMessage() );
	}

	/**
	 * Called before the scheduler is going to be shutdown
	 */
	@Override
	public void onShutdown() {
		logger.debug( "Mail Spool Scheduler has been shutdown." );
	}

	/**
	 * Called after the scheduler has registered all schedules
	 */
	@Override
	public void onStartup() {
		logger.debug( "The Mail Spool Scheduler has been started" );
	}

	/**
	 * Called whenever ANY task fails
	 *
	 * @task The task that got executed
	 *
	 * @exception The ColdFusion exception object
	 */
	@Override
	public void onAnyTaskError( ScheduledTask task, Exception exception ) {
		logger.error( "An error occurred while execute the spool task " + task.getName() + ". The messge received was: " + exception.getMessage() );
	}

	/**
	 * Called whenever ANY task succeeds
	 *
	 * @task The task that got executed
	 *
	 * @result The result (if any) that the task produced
	 */
	@Override
	public void onAnyTaskSuccess( ScheduledTask task, Optional<?> result ) {
		logger.debug( "Mail Spool scheduled task " + task.getName() + " successfully completed." );
	}

}
