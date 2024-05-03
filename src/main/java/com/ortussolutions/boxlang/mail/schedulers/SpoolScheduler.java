package com.ortussolutions.boxlang.mail.schedulers;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ortussolutions.boxlang.mail.components.Mail;
import com.ortussolutions.boxlang.mail.util.MailKeys;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.async.tasks.BaseScheduler;
import ortus.boxlang.runtime.async.tasks.ScheduledTask;
import ortus.boxlang.runtime.cache.ICacheEntry;
import ortus.boxlang.runtime.cache.providers.ICacheProvider;
import ortus.boxlang.runtime.dynamic.casters.LongCaster;
import ortus.boxlang.runtime.dynamic.casters.StructCaster;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

public class SpoolScheduler extends BaseScheduler {

	static final Key				spoolCache				= MailKeys.mailUnsent;
	static final Key				bounceCache				= MailKeys.mailBounced;

	static final double				minuteToMilisMulitplier	= 60000d;

	private static final BoxRuntime	runtime					= BoxRuntime.getInstance();
	private static final IStruct	moduleSettings			= runtime.getModuleService().getModuleSettings( MailKeys._MODULE_NAME );

	private static final Boolean	logEnabled				= moduleSettings.getAsBoolean( MailKeys.logEnabled );
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
			        Key.objectStore, MailKeys.fileSystemStore,
			        Key.directory, moduleSettings.get( MailKeys.spoolDirectory ),
			        Key.defaultTimeout, moduleSettings.get( MailKeys.spoolTimeout ),
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
			        Key.objectStore, MailKeys.fileSystemStore,
			        Key.directory, moduleSettings.get( MailKeys.bounceDirectory ),
			        Key.defaultTimeout, moduleSettings.get( MailKeys.bounceTimeout ),
			        Key.useLastAccessTimeouts, false,
			        Key.evictCount, 0
			    )
			);
		}

		long spoolIntervalMillis = LongCaster.cast( moduleSettings.getAsDouble( MailKeys.spoolInterval ) * minuteToMilisMulitplier );

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
				    Email message = ( Email ) entry.value().get();
				    message.send();
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
		System.out.println( "Spool Task failed: " + exception.getMessage() );
	}

	/**
	 * Called before the scheduler is going to be shutdown
	 */
	@Override
	public void onShutdown() {
		System.out.println( "[onShutdown] ==> The ExampleScheduler has been shutdown" );
	}

	/**
	 * Called after the scheduler has registered all schedules
	 */
	@Override
	public void onStartup() {
		System.out.println( "[onStartup] ==> The ExampleScheduler has been started" );
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
		System.out.println( "[onAnyTaskError] ==> " + task.getName() + " ran into an error: " + exception.getMessage() );
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
		System.out.println( "[onAnyTaskSuccess] ==>  " + task.getName() + " task successful!" );
	}

	/**
	 * Called before ANY task runs
	 *
	 * @task The task about to be executed
	 */
	@Override
	public void beforeAnyTask( ScheduledTask task ) {
		System.out.println( "[beforeAnyTask] ==> Before task: " + task.getName() );
	}

	/**
	 * Called after ANY task runs
	 *
	 * @task The task that got executed
	 *
	 * @result The result (if any) that the task produced
	 */
	@Override
	public void afterAnyTask( ScheduledTask task, Optional<?> result ) {
		System.out.println( "[afterAnyTask] ==> After task: " + task.getName() );
	}

}
