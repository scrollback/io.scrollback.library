Scrollback Android SDK
======================

## Usage

```
public class MainActivity extends FragmentActivity {
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main_activity);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        transaction.add(R.id.main_framelayout, fragment);
        transaction.commit();
    }

    ScrollbackFragment fragment = new ScrollbackFragment() {
        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event) {
            return super.onKeyDown(keyCode, event);
        }
    };

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return fragment.onKeyDown(keyCode, event);
    }
}
```

## Debug mode

```
fragment.setEnableDebug(true);
```

## API

```
fragment.setMessageHandler(new ScrollbackMessageHandler() {
    @Override
    public void onNavMessage(NavMessage message) {

    }

    @Override
    public void onAuthMessage(AuthStatus message) {

    }

    @Override
    public void onFollowMessage(FollowMessage message) {

    }

    @Override
    public void onReadyMessage(ReadyMessage message) {

    }
});
```
