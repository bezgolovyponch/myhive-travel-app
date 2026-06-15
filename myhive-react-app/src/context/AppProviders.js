import {CatalogProvider} from './CatalogContext';
import {TripProvider} from './TripContext';
import {DestinationModalProvider} from './DestinationModalContext';

// Composes the three focused contexts that replace the former single
// AppContext. Order is arbitrary — the three are independent.
export function AppProviders({children}) {
    return (
        <CatalogProvider>
            <TripProvider>
                <DestinationModalProvider>
                    {children}
                </DestinationModalProvider>
            </TripProvider>
        </CatalogProvider>
    );
}
