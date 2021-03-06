package transponders.transmob;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;

import android.annotation.SuppressLint;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentManager.OnBackStackChangedListener;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.preference.PreferenceManager;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.AdapterView.OnItemSelectedListener;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnInfoWindowClickListener;
import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
//import com.google.android.gms.maps.GoogleMap.OnMyLocationButtonClickListener;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;

/**
 * The Android activity that shows the map with the current location of the
 * device and shows the nearby stops as markers on the map. Currently, this is
 * also the home screen of the application.
 * 
 * @author Transponders
 * @version 2.0
 */
public class NearbyStops extends ActionBarActivity implements
GooglePlayServicesClient.ConnectionCallbacks, OnConnectionFailedListener, com.google.android.gms.location.LocationListener,
LoadingListener
{
	/**
	 * Set the default location in case the application cannot detect the
	 * current location of the device.
	 */
	private static final LatLng DEFAULT_LOCATION = new LatLng(-27.498037,153.017823);
	private final String TITLE = "Nearby Stops";
	public static final int NUM_PAGES = 5;
	public static final String TUTORIAL_SETTING = "SHOW_TUTORIAL";
	public static final String SELECTED_STOP_NAME = "SELECTED_STOP_NAME";
	int RADIUS = 1000;
	int NUM_OF_STOPS = 25;

	public enum StackState {
		NearbyStops, ShowRoute
	}

	// Map and markers
	private GoogleMap mMap;
	private Marker userPos;
	private Marker clickPos;
	private StopDataLoader stopLoader;
	private RouteStopsLoader routeStopsLoader;
	private SupportMapFragment mapFrag;
	private boolean updatedOnce;
	private ArrayList<Stop> selectedStops;
	private Route selectedRoute;
	private Trip selectedTrip;
	private Trip selectedTrip2;
	private LatLng userLatLng;
	private ArrayList<Marker> stopMarkers;
	private HashMap<Marker, Stop> stopMarkersMap;
	private CountDownTimer splashScreenTimer;
	
	private Polyline polyline;
	private HashMap<Integer, StackState> stackStatesMap;
	private int previousBackStackPosition;
	private boolean viewingMap;

	// Navigation drawer
	private DrawerLayout mDrawerLayout;
	private ListView mDrawerList;
	private ActionBarDrawerToggle mDrawerToggle;
	private CharSequence mDrawerTitle;
	private CharSequence mTitle;
	String[] menuList;

	FrameLayout splashFrame;
	private ViewPager mPager;
	private PagerAdapter mPagerAdapter;
	
	private LocationClient mLocationClient;
	private LocationRequest mLocationRequest;
	// Handle to SharedPreferences for this app
	SharedPreferences settings;
    // Handle to a SharedPreferences editor
    SharedPreferences.Editor mEditor;
    // Milliseconds per second
    private static final int MILLISECONDS_PER_SECOND = 1000;
    // Update frequency in seconds
    public static final int UPDATE_INTERVAL_IN_SECONDS = 5;
    // Update frequency in milliseconds
    private static final long UPDATE_INTERVAL =
            MILLISECONDS_PER_SECOND * UPDATE_INTERVAL_IN_SECONDS;
    // The fastest update frequency, in seconds
    private static final int FASTEST_INTERVAL_IN_SECONDS = 1;
    // A fast frequency ceiling in milliseconds
    private static final long FASTEST_INTERVAL =
            MILLISECONDS_PER_SECOND * FASTEST_INTERVAL_IN_SECONDS;
    boolean mUpdatesRequested = true;

    // For route display
    TableLayout table;
    TextView colorBox;
	TextView tripCode;
	TextView routeInfo;
	
	// For testing purposes
	private JourneyPlanner jpFragment = null;
	private MaintenanceNewsFragment mnFragment = null;
	private GocardLoginFragment gclFragment;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		
		getSupportActionBar().hide();
		
		setContentView(R.layout.activity_main);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		splashFrame = (FrameLayout) findViewById(R.id.splash_screen_container); 
		splashFrame.setOnTouchListener(new OnTouchListener()
		{
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				return true;
			}	
		});
		
		stopMarkers = new ArrayList<Marker>();
		stopMarkersMap = new HashMap<Marker, Stop>();
		
		// Set the fragment manager so it will pop elements from the stackStates
		FragmentManager manager = getSupportFragmentManager();
		manager.addOnBackStackChangedListener(getBackListener());
		stackStatesMap = new HashMap<Integer,StackState>();
		stackStatesMap.put(0, StackState.NearbyStops);
		previousBackStackPosition = 0;
		viewingMap = true;

		initializeNavigationDrawer();

		settings = PreferenceManager.getDefaultSharedPreferences(this);
		boolean showTut = settings.getBoolean(TUTORIAL_SETTING, true);
		RADIUS = Integer.parseInt(settings.getString("pref_radius", "1000"));
		NUM_OF_STOPS = settings.getInt("pref_num_of_stops", 25);

		// For debugging, uncomment this line below so that the tutorial doesn't show at all.
		// showTut = false;

        // Create the LocationRequest object
        mLocationRequest = LocationRequest.create();
        // Use high accuracy
        mLocationRequest.setPriority(
                LocationRequest.PRIORITY_HIGH_ACCURACY);
        // Set the update interval to 5 seconds
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        // Set the fastest update interval to 1 second
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);
        /*
         * Create a new location client, using the enclosing class to
         * handle callbacks.
         */
        mLocationClient = new LocationClient(this, this, this);
        // Start with updates turned off
        mUpdatesRequested = true;
        mLocationClient.connect();
	
		if (showTut)
			showFirstTimeTutorial();
		
		// initialise the map and show the default initial function: NearbyStops
		mapInit();
		showNearbyStops();
		
		// Show the splash screen for 2.5 seconds
		splashScreenTimer = new CountDownTimer (2500, 1000) {

			@Override
			public void onFinish() {
				closeSplashScreen();
			}

			@Override
			public void onTick(long arg0) {}
			
		}.start();
	}
	
	/**
	 * The method to initialize the side bar navigation drawer.
	 */
	public void initializeNavigationDrawer()
	{
		// Set the title and the content of the navigation drawer.
		mTitle = TITLE;
		mDrawerTitle = getTitle();
		menuList = getResources().getStringArray(R.array.menu_array);
		mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout_ns);
		mDrawerList = (ListView) findViewById(R.id.left_drawer_ns);

		// set a custom shadow that overlays the main content when the drawer opens
		mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow,
				GravityCompat.START);
		
		mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

		// set up the drawer's list view with items and click listener
		ArrayAdapter<String> adapter = new MenuAdapter(this, menuList);
		mDrawerList.setAdapter(adapter);
		mDrawerList.setOnItemClickListener(new DrawerItemClickListener());

		int position = 0;
		mDrawerList.setItemChecked(position, true);

		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setTitle(TITLE);

		mDrawerToggle = new ActionBarDrawerToggle(this, /* host Activity */
		mDrawerLayout, /* DrawerLayout object */
		R.drawable.ic_drawer, /* nav drawer image to replace 'Up' caret */
		R.string.drawer_open, /* "open drawer" description for accessibility */
		R.string.drawer_close /* "close drawer" description for accessibility */
		) {
			public void onDrawerClosed(View view) {
				getSupportActionBar().setTitle(mTitle);
			}

			public void onDrawerOpened(View drawerView) {
				getSupportActionBar().setTitle(mDrawerTitle);
			}
		};

		mDrawerLayout.setDrawerListener(mDrawerToggle);
	}

	/**
	 * A method to move the camera when the user touch the map to set a new
	 * location that will have the nearby stops generated around.
	 * 
	 * @param location
	 *            the latitude and longitude of the new location.
	 */
	public void locationChanged(LatLng location) 
	{
		//mMap.moveCamera(CameraUpdateFactory.newLatLng(location));
		mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, (float) 14.8), 2000, null);
		
		settings = PreferenceManager.getDefaultSharedPreferences(this);
		RADIUS = Integer.parseInt(settings.getString("pref_radius", "1000"));
		NUM_OF_STOPS = settings.getInt("pref_num_of_stops", 25);
		
		stopLoader.requestStopsNear(location.latitude, location.longitude, RADIUS, NUM_OF_STOPS);
	}

	/**
	 * A method to set the action bar. The action bar home/up action should open
	 * or close the drawer. ActionBarDrawerToggle will take care of this.
	 * 
	 * @param item
	 *            the MenuItem that is selected.
	 */
	public boolean onOptionsItemSelected(MenuItem item) {
		if (mDrawerToggle.onOptionsItemSelected(item)) {
			return true;
		}
		else
		{
			switch (item.getItemId()) 
			{
				case R.id.action_settings:
					
					FragmentManager manager = getSupportFragmentManager();
					Fragment fragment = new SettingsFragment();
					
					FragmentTransaction transaction = manager.beginTransaction();
					transaction.replace(R.id.content_frame, fragment);
					transaction.addToBackStack(null);
					transaction.commitAllowingStateLoss();

					// update selected item and title, then close the drawer
					mDrawerList.setItemChecked(4, true);
					setTitle(menuList[4]);
					
					break;
				default:
					break;
			}
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * A listener class that handles what will happen when the item inside the
	 * navigation drawer is clicked.
	 * 
	 * @author Transponders
	 * @version 1.0
	 */
	private class DrawerItemClickListener implements
			ListView.OnItemClickListener {
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position,
				long id) {

			selectItem(position);

		}
	}

	/**
	 * A method that defines what will happen when the user clicks a specific
	 * menu item in the navigation drawer. The method will start a new activity
	 * according to the selected menu.
	 * 
	 * @param pos
	 *            the position of the menu item that is clicked.
	 */
	private void selectItem(int pos) {
		Fragment fragment = new Fragment();
		FragmentManager manager = getSupportFragmentManager();
		// manager.addOnBackStackChangedListener(getBackListener());

		switch (pos) {
		case 0:
			manager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
			mDrawerList.setItemChecked(pos, true);
			setTitle(TITLE);
			mDrawerLayout.closeDrawer(mDrawerList);
			updatedOnce = false;
			showNearbyStops();
			if (userLatLng.latitude != 0 && userLatLng.longitude != 0) {
				locationChanged(userLatLng);
			}
			return;
		case 1:
			jpFragment = new JourneyPlanner();
			fragment = jpFragment;
			Bundle args = new Bundle();
			double[] userLoc = { userLatLng.latitude, userLatLng.longitude };
			args.putDoubleArray(JourneyPlanner.ARGS_USER_LOC, userLoc);
			fragment.setArguments(args);
			break;
		case 2:
			gclFragment = new GocardLoginFragment();
			fragment = gclFragment;
			break;
		case 3:
			mnFragment = new MaintenanceNewsFragment();
			fragment = mnFragment;
			break;
		case 4:
			fragment = new SettingsFragment();
			break;
		default:
			break;
		}
		
		FragmentTransaction transaction = manager.beginTransaction();
		transaction.replace(R.id.content_frame, fragment);
		transaction.addToBackStack(null);
		transaction.commitAllowingStateLoss();

		// update selected item and title, then close the drawer
		mDrawerList.setItemChecked(pos, true);
		setTitle(menuList[pos]);
		mDrawerLayout.closeDrawer(mDrawerList);
	}
	
	public void mapInit() 
	{
		LatLng center = DEFAULT_LOCATION;

		mapFrag = (SupportMapFragment) getSupportFragmentManager()
				.findFragmentById(R.id.map);

		mMap = mapFrag.getMap();
		// mMap = ((MapFragment)
		// getFragmentManager().findFragmentById(R.id.map)).getMap();

		while (mMap == null) {
			// The application is still unable to load the map.
		}
		mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(center, 15));
		mMap.setMyLocationEnabled(false);
		
		
		stopLoader = new StopDataLoader(mMap, stopMarkers, stopMarkersMap);
		stopLoader.registerListener(this);
		routeStopsLoader = new RouteStopsLoader(mMap, stopMarkers,
				stopMarkersMap, polyline);
		routeStopsLoader.registerListener(this);
		userLatLng = new LatLng(0,0);
		updatedOnce = false;
		
		final Button button = (Button) findViewById(R.id.bLocateMe);
		button.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				/*updatedOnce = false;
				showNearbyStops();
				if (userLatLng.latitude != 0 && userLatLng.longitude != 0) {
					locationChanged(userLatLng);
				}*/
				startUpdates();
				
			}
		});
	}


	/**
	 * Sets the map to showing nearby stops. Expects to be called when the backstack indicates the 
	 * reaching of the initial position
	 */
	public void showNearbyStops() {
		if (userPos == null) {
			userPos = mMap.addMarker(new MarkerOptions()
					.position(DEFAULT_LOCATION)
					.title("Your Position")
					.visible(false)
					.icon(BitmapDescriptorFactory
							.fromResource(R.drawable.location_geo_border)));
		}
		if (clickPos == null) {
			clickPos = mMap.addMarker(new MarkerOptions()
					.position(DEFAULT_LOCATION)
					.title("Your Selected Position")
					.visible(false)
					.icon(BitmapDescriptorFactory
							.fromResource(R.drawable.chosen_geo_border)));
		}

		//Reload the stops if any from previous nearbystops
		
		stopLoader.addSavedStopMarkersToMap(true);
		routeStopsLoader.removeEstimatedServicesFromMap();
		routeStopsLoader.removeLineFromMap();
		getSupportActionBar().setTitle(TITLE);

		mMap.setOnInfoWindowClickListener(new OnInfoWindowClickListener() {

			@Override
			public void onInfoWindowClick(Marker marker) {
				
				//When the marker's info window is clicked, open the DisplayTimetableFragment
				Stop stop = stopLoader.getIdOfMarker(marker);
				if (stop != null) {
					ArrayList<Stop> stops;
					if (stop.hasParent()) {
						stops = stopLoader.getStopsFromParent(stop);
					} else {
						stops = new ArrayList<Stop>();
						stops.add(stop);
					}
					setSelectedStops(stops);

					openTimetableFragment(marker.getTitle());
				}
			}
		});

		mMap.setOnMapClickListener(new OnMapClickListener() {

			@Override
			public void onMapClick(LatLng arg0) {
				//When a map location is clicked, show the selected location marker and start change
				clickPos.setVisible(true);
				clickPos.setPosition(arg0);
				locationChanged(arg0);
			}
		});
		
			
		// Acquire a reference to the system Location Manager
		LocationManager locationManager = (LocationManager) this
				.getSystemService(Context.LOCATION_SERVICE);

		// Define a listener that responds to location updates
		LocationListener locationListener = new LocationListener() {
			public void onLocationChanged(Location location) {
				
				// Called when a new location is found by the network location
				// provider.
				userLatLng = new LatLng(location.getLatitude(),
						location.getLongitude());
				userPos.setVisible(true);
				userPos.setPosition(userLatLng);
				locationChanged(userLatLng);
				
				updatedOnce = true;
				Log.d("Location", "Location changed");
			}

			public void onStatusChanged(String provider, int status,
					Bundle extras) {
			}

			public void onProviderEnabled(String provider) {
			}

			public void onProviderDisabled(String provider) {
			}
		};

		
		
		TableLayout table = (TableLayout) findViewById(R.id.triptable); 
		table.setVisibility(View.GONE);
		mMap.setMyLocationEnabled(false);
		final Button button = (Button) findViewById(R.id.bLocateMe);
		button.setVisibility(View.VISIBLE);
	}

	/**
	 * The method to initialize the first time tutorial.
	 */
	public void showFirstTimeTutorial() 
	{
		mPager = (ViewPager) findViewById(R.id.pager);
		mPagerAdapter = new TutorialPagerAdapter(getSupportFragmentManager(), mPager);
		mPager.setAdapter(mPagerAdapter);
	}
	
	/**
	 * The method used in the route map page. This method loads the route map and
	 * information for services that has ended on the current day.
	 */
	public void showRoute()
	{
		routeStopsLoader.requestRouteStops(selectedRoute);
		
		setTitle("Service Route Map");
		FrameLayout mapFrame = (FrameLayout) findViewById(R.id.map_frame);
		LinearLayout.LayoutParams frameParam = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 3);
		mapFrame.setLayoutParams(frameParam);
		
		table = (TableLayout) findViewById(R.id.triptable);
		LinearLayout.LayoutParams tableParam = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1);
		table.setLayoutParams(tableParam);
		
		colorBox = (TextView) findViewById(R.id.color_box);
		tripCode = (TextView) findViewById(R.id.trip_code);
		routeInfo = (TextView) findViewById(R.id.trip_description); 
		
		long stopType = selectedRoute.getType();
		if(stopType == Route.TransportType.BUS)
		{
        	mMap.animateCamera(CameraUpdateFactory.zoomTo((float) 13.5), 2000, null);
         	colorBox.setBackgroundResource(R.color.bus_green);
		}
		else if(stopType == Route.TransportType.TRAIN)
		{
			LatLng center = new LatLng(-27.471794, 153.029895);
        	mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(center, (float) 11), 2000, null);
			colorBox.setBackgroundResource(R.color.train_orange);
        }
        else
        {
        	LatLng center = new LatLng(-27.471999, 153.029895);
        	mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(center, (float) 12.5), 2000, null);
        	colorBox.setBackgroundResource(R.color.ferry_blue);
        }
		 
		tripCode.setText(selectedRoute.getCode());
		routeInfo.setText(selectedRoute.getDescription());
		
		Spinner tripChoices = (Spinner) findViewById(R.id.trip_choice);
		tripChoices.setVisibility(View.INVISIBLE);
		
		TextView additionalInfo = (TextView) findViewById(R.id.additional_info);
		Calendar c = Calendar.getInstance();
		additionalInfo.setText("Showing route on " + c.get(Calendar.DAY_OF_MONTH) 
								+ "/" + (c.get(Calendar.MONTH)+1) 
								+ "/" + c.get(Calendar.YEAR));
		additionalInfo.setVisibility(View.VISIBLE);
		
		table.setVisibility(View.VISIBLE);
		
		mMap.setMyLocationEnabled(true);
		final Button button = (Button) findViewById(R.id.bLocateMe);
		button.setVisibility(View.GONE);
	}

	/**
	 * The method used in the route map page. This method loads the trip map and
	 * information for services that still have trips for the current day.
	 */
	public void showTrip() 
	{	
		routeStopsLoader.requestTripStops(selectedTrip);
		
		setTitle("Service Route Map");
		FrameLayout mapFrame = (FrameLayout) findViewById(R.id.map_frame);
		LinearLayout.LayoutParams frameParam = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 3);
		mapFrame.setLayoutParams(frameParam);
		
		table = (TableLayout) findViewById(R.id.triptable);
		LinearLayout.LayoutParams tableParam = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1);
		table.setLayoutParams(tableParam);
		
		colorBox = (TextView) findViewById(R.id.color_box);
		tripCode = (TextView) findViewById(R.id.trip_code);
		routeInfo = (TextView) findViewById(R.id.trip_description); 
		
		long stopType = selectedTrip.getRoute().getType();
		if(stopType == Route.TransportType.BUS)
		{
			mMap.animateCamera(CameraUpdateFactory.zoomTo((float) 13.5), 2000, null);
         	colorBox.setBackgroundResource(R.color.bus_green);
		}
		else if(stopType == Route.TransportType.TRAIN)
		{
			LatLng center = new LatLng(-27.471794, 153.029895);
        	mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(center, (float) 11), 2000, null);
			colorBox.setBackgroundResource(R.color.train_orange);
        }
        else
        {
        	LatLng center = new LatLng(-27.464000, 153.029895);
        	mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(center, (float) 12.7), 2000, null);
        	colorBox.setBackgroundResource(R.color.ferry_blue);
        }
		
		tripCode.setText(selectedTrip.getRoute().getCode());
		routeInfo.setText(selectedTrip.getRoute().getDescription());
		
		Spinner tripChoices = (Spinner) findViewById(R.id.trip_choice);
		tripChoices.setVisibility(View.VISIBLE);
		
		String[] services;
		if(selectedTrip2 != null)
		{
			services = new String[2];
			services[0] = "Service 1";
			services[1] = "Service 2";
		}
		else
		{
			services = new String[1];
			services[0] = "Service 1";
		}
		
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, services);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        tripChoices.setAdapter(adapter);
        tripChoices.setOnItemSelectedListener(new TripDropdownListener());
		
        TextView additionalInfo = (TextView) findViewById(R.id.additional_info);
        additionalInfo.setText("Current service location is estimated based on schedule.");
        additionalInfo.setVisibility(View.VISIBLE);
		table.setVisibility(View.VISIBLE);
		
		mMap.setMyLocationEnabled(true);
		final Button button = (Button) findViewById(R.id.bLocateMe);
		button.setVisibility(View.GONE);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		// Sync the toggle state after onRestoreInstanceState has occurred.
		mDrawerToggle.syncState();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		mDrawerToggle.onConfigurationChanged(newConfig);
	}

	@SuppressLint("NewApi")
	@Override
	public void setTitle(CharSequence title) {
		mTitle = title;
		getSupportActionBar().setTitle(mTitle);
	}

	/**
	 * The method to load the timetable fragment for a selected stop.
	 */
	public void openTimetableFragment(String stopName) {
		Fragment fragment = new Fragment();
		FragmentManager manager = getSupportFragmentManager();
		// manager.addOnBackStackChangedListener(getBackListener());
		fragment = new DisplayRoutesFragment();
		
		Bundle args = new Bundle();
		args.putString(SELECTED_STOP_NAME, stopName);
		fragment.setArguments(args);

		FragmentTransaction transaction = manager.beginTransaction();
		transaction.replace(R.id.content_frame, fragment);
		transaction.setCustomAnimations(android.R.anim.slide_in_left,
				android.R.anim.slide_out_right);
		transaction.addToBackStack(null);
		transaction.commitAllowingStateLoss();

		setTitle("Timetable");
	}

	/**
	 * A helper method to create custom listener for the back functionalities between fragments.
	 * 
	 * @return OnBackStackChangedListener the custom backstack listener.
	 */
	public OnBackStackChangedListener getBackListener() {
		OnBackStackChangedListener result = new OnBackStackChangedListener() {
			public void onBackStackChanged() {
				FragmentManager manager = getSupportFragmentManager();

				if (manager != null) {
					
					int stackCount = manager.getBackStackEntryCount();
					
					
					// If the current stack change is a "back" command
					if (previousBackStackPosition >= stackCount){
										
						
						Fragment currFrag = (Fragment) manager.getFragments()
								.get(stackCount);
						
						if (currFrag != null) {
							
							currFrag.onResume();
							
							if (currFrag.getClass() == SupportMapFragment.class) {
								
								// assume seeing NearbyStops
	
								StackState state;
								
								
								// Get the state of this SupportMapFragment to determine action
								state = stackStatesMap.get(stackCount);
								
	
								if (state == StackState.NearbyStops) {
									showNearbyStops();
								} else if (state == StackState.ShowRoute) {
									showTrip();
								}

							} else {
								
								// currently in some Fragment that does not contain a map
								
								viewingMap = false;
							}
						}
					}
					previousBackStackPosition = stackCount;
					
				}
			}
		};

		return result;
	}

	/**
	 * Getter method of the selected stops object.
	 * 
	 * @return ArrayList<Stop> The list of selected stops.
	 */
	public ArrayList<Stop> getSelectedStops() {
		return selectedStops;
	}

	/**
	 * Setter method of the selected stops object.
	 * 
	 * @param stops
	 *            the ArrayList of selected stops.
	 */
	public void setSelectedStops(ArrayList<Stop> stops) {
		this.selectedStops = stops;
	}

	public Route getSelectedRoute() {
		return selectedRoute;
	}

	
	public void setSelectedRoute(Route route) {
		selectedRoute = route;
	}
	
	public void setSelectedTrip(Trip trip) {
		selectedTrip = trip;
	}
	
	public void setSelectedTrip(Trip trip, Trip trip2) {
		selectedTrip = trip;
		selectedTrip2 = trip2;
	}
	
	/**
	 * Adds a map state to the stack. To be called whenever move forward to show the map
	 * @param state
	 */
	public void addStateToStack(StackState state) {
		FragmentManager manager = getSupportFragmentManager();
		int stackCount = manager.getBackStackEntryCount();
		stackStatesMap.put(stackCount+1, state);
		
	}

	/**
	 * Removes all markers from the Google map
	 */
	public void removeAllMarkers() {
		for (Marker m : stopMarkers) {
			m.setVisible(false);
			m.remove();
		}
	}

	/**
	 * Private helper class that is the adapter of first time tutorial.
	 * 
	 * @author Transponders
	 * @version 1.0
	 */
	private class TutorialPagerAdapter extends FragmentStatePagerAdapter 
	{
		private ViewPager pagerView;

		public TutorialPagerAdapter(FragmentManager fm, ViewPager parent) 
		{
			super(fm);
			pagerView = parent;
		}

		@Override
		public Fragment getItem(int position) 
		{
			return TutorialFragment.create(position, pagerView);
		}

		@Override
		public int getCount()
		{
			return NUM_PAGES;
		}
	}

	@Override
	public void onConnected(Bundle arg0) {
		
        // Display the connection status
        // If already requested, start periodic updates
        if (mUpdatesRequested) {
            mLocationClient.requestLocationUpdates(mLocationRequest, this);
        }
	}

	@Override
	public void onDisconnected() {
		
	}
	
	@Override
    public void onLocationChanged(Location location) {
	 
		// Called when a new location is found by the network location
		// provider.
		userLatLng = new LatLng(location.getLatitude(),
				location.getLongitude());
		userPos.setVisible(true);
		userPos.setPosition(userLatLng);
		//if (!updatedOnce) {
			locationChanged(userLatLng);
		//}
		updatedOnce = true;
		Log.d("Location", "Location changed");
		mLocationClient.removeLocationUpdates(this);
		
    }

	public void startUpdates() {
		mLocationClient.requestLocationUpdates(mLocationRequest, this);
	}

	@Override
	public void onConnectionFailed(ConnectionResult arg0) {
		
	}
	
	/**
	 * Private helper class to instantiate the trip dropdown choice in the 
	 * route display interface.
	 * 
	 * @author Transponders
	 * @version 1.0
	 */
	private class TripDropdownListener implements OnItemSelectedListener 
    {
    	public void onItemSelected(AdapterView<?> parent, View view, int pos, long id)
    	{
    		removeAllMarkers();
    		
    		if(pos == 0) 
    		{
    			routeStopsLoader.requestTripStops(selectedTrip);
    			tripCode.setText(selectedTrip.getRoute().getCode());
    			routeInfo.setText(selectedTrip.getRoute().getDescription());
    		}
    		else if(selectedTrip2 != null)
    		{
    			routeStopsLoader.requestTripStops(selectedTrip2);
    			tripCode.setText(selectedTrip2.getRoute().getCode());
    			routeInfo.setText(selectedTrip2.getRoute().getDescription());
    		}
    	}
    	
    	public void onNothingSelected(AdapterView<?> arg0)
    	{}
    }
	
	public void closeSplashScreen() {

		splashFrame.setVisibility(View.GONE);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
		getSupportActionBar().show();
		mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
	}

	@Override
	public void onStateChange(boolean state) {
		setProgressBarIndeterminateVisibility(state);
	}
	
	/*Testing functions */
	public StopDataLoader getStopDataLoader() {
		return stopLoader;
	}

	public RouteStopsLoader getRouteStopsLoader() {
		return routeStopsLoader;
	}

	public ArrayList<Marker> getStopMarkers() {
		return stopMarkers;
	}
	public Fragment getContentFragment() {
		FragmentManager manager = getSupportFragmentManager();
		return manager.findFragmentById(R.id.content_frame);
	}
	
	public JourneyPlanner getJourneyPlannerFragment()
	{
		return jpFragment;
	}
	
	public MaintenanceNewsFragment getMaintenanceNewsFragment()
	{
		return mnFragment;
	}
	
	/**
	 * Change the Fragment in the content_frame to the GocardLoginFragment 
	 */
	public void openGocardLoginFragment() {
		Fragment fragment = new Fragment();
		FragmentManager manager = getSupportFragmentManager();
		// manager.addOnBackStackChangedListener(getBackListener());
	
		fragment = new GocardLoginFragment();
		
		
		FragmentTransaction transaction = manager.beginTransaction();
		transaction.replace(R.id.content_frame, fragment);
		transaction.addToBackStack(null);
		transaction.commitAllowingStateLoss();
	}
	/*End of Testing functions */
}
