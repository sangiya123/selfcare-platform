/**
 * Selfcare App — Entry point.
 *
 * Registers the App component with the React Native AppRegistry.
 *
 *   - Android: appName in AndroidManifest.xml is "selfcareSelfcare"
 *   - iOS:     appName in AppDelegate.mm is "selfcareSelfcare"
 */

import { AppRegistry } from 'react-native';
import App from './App';
import { name as appName } from './app.json';

AppRegistry.registerComponent(appName, () => App);
