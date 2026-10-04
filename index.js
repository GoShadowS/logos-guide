/**
 * Точка входа приложения «ЛОГОС: Путеводитель».
 *
 * ВАЖНО: 'react-native-gesture-handler' должен импортироваться первым,
 * иначе жесты (зум, перетаскивание карты) не будут работать.
 */
import 'react-native-gesture-handler';
import { registerRootComponent } from 'expo';
import App from './src/App';

registerRootComponent(App);
