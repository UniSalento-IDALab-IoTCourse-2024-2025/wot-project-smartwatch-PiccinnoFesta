# Tremor Monitoring: Parkinson's Disease

## Panoramica

Questo progetto nasce per fornire un supporto al personale sanitario per il monitoraggio a distanza delle condizioni di un paziente domiciliarizzato
affetto dal disturbo di Parkinson.

Durante la realizzazione, i punti chiave che hanno poi influenzato la scelta dell'architettura sono stati:
- L'attenzione al paziente: rendere l'applicazione il meno invasiva ed il meno onerosa possibile a livello di interazione umana.
- Creare una struttura facilmente scalabile, sia orizzontalmente che verticalmente, favorendo il disaccoppiamennto più completo tra le varie componenti
- Fornire una struttura sicura, in quanto a che vedere con dati sensibili
- Garantire ad un medico che si interfaccia con l'applicazione di poter modificare le funzioni in base a varie esigenze diagnostiche.

Per poter sviluppare un' architettura del genere, i dati vengono prelevati tramite sensori **IMU** (Inertial MeasurementSensors) e attraverso il **BLE** (Bluetooth Low Energy), trasmessi ad un *raspberry Pi 5*, il quale si occupa della conversione e preparazione dei dati per l'algoritmo di Machine Learning. Questi dati sono poi passati ad una piattaforma cloud (**AWS**), dove risiedono oltre all'algoritmo di intelligenza artificiale, anche i microservizi per la gestione utenti e l'interfaccia frontend

Per la persistenza dei dati forniti dai sensori, e i risultati dell'inferenza, si è scelto di sfruttare il servizio cloud **S3** (Simple storage service), per disaccoppiare l'accesso dei dati, dall'inferenza in sé.


## Architettura generale

L'architettura si basa sull'utilizzo di microservizi, indipendenti, modulari e scalabili. Questi risiedono sull'infrastruttura cloud e l'accesso è fornito dai routing di **API GATEWAY**, il quale sfrutta anche il paradigma serverless (**Lambda functions**) per determinate operazioni.
Il tutto è stato poi containerizzato tramite Docker.

<div align="center">
  <img src="img/FullProject.png" alt="Descrizione" width="400"/>
</div>


### Backend
- **UserService**: responsabile per la  gestione utenti. Fornisce operazioni CRUD sulle due entità principali della soluzione, cioè *Doctor* e *Patient*. Inoltre gestisce  registrazione, autenticazione e autorizzazione per gli utenti per cui è predisposto il servizio (*Doctor*).
- **SmartWatchService**: servizio installato localmente sul dispositivo, il quale tramite BLE si occupa di comunicare col raspberry Pi, responsabile dell'invio dei dati sul cloud
- **BLEserver**: servizio installato localmente sul raspberry, si occupa di fornire un server per la ricezione dei dati sensoriali, la conversione in un formato dati valido e l'invio di questi sul database remoto (*S3*)
- **TremorAnalysis**: Si occupa dell'esecuzione dell'algoritmo di intelligenza artificiale, basato sulla libreria di ParaDigMa. Fornisce dei rest controller per poter avviare l'inferenza su richiesta, e si interfaccia col gateway per gestire autonomamente il recupero/invio dei dati sul database remoto

### Frontend

L'interfaccia web, esposta da un microservizio sul cloud, e sviluppata con React + Vite, permette l'accesso ai medici autorizzati nel sistema. Questi possono visualizzare le informazioni sui pazienti e gestirli nel database, nonchè visualizzare in una pagina dedicata, le statistiche sul tremore rilevato nel tempo per un determinato paziente.

## Repositories dei componenti
- UserService: [UserService](https://github.com/UniSalento-IDALab-IoTCourse-2024-2025/wot-project-UserService-PiccinnoFesta)
- TremorAnalisys: [Tremor Analisys](https://github.com/UniSalento-IDALab-IoTCourse-2024-2025/wot-project-TremorAnalysis-PiccinnoFesta)
- SmartWatchService: [Smartwatch](https://github.com/UniSalento-IDALab-IoTCourse-2024-2025/wot-project-smartwatch-PiccinnoFesta)
- BLE server: [BLE Server](https://github.com/UniSalento-IDALab-IoTCourse-2024-2025/wot-project-BLEserver-PiccinnoFesta)
- Frontend: [Frontend](https://github.com/UniSalento-IDALab-IoTCourse-2024-2025/wot-project-Frontend-PiccinnoFesta)

## SmartWatchService

Questo servizio, sviluppato per dispositivi wearable Android ha lo scopo di raccogliere dati di movimento inerziali tramite i  sensori e inviarli sfruttando il  Bluetooth Low Energy (BLE) a un dispositivo remoto. Il tutto avviene in background, grazie a un servizio in foreground che garantisce l’esecuzione continua e stabile anche a schermo spento.

---

# Funzionalità principali

- Rilevamento e connessione automatica a dispositivi BLE con un `Service UUID` specifico.
- Raccolta continua di dati dai sensori 
- Invio dei dati in formato JSON via BLE a una caratteristica GATT.
- Gestione delle sessioni tramite batch numerati per tracciare i progressi e categorizzare i pacchetti inviati in batch definiti.
- Esecuzione in background con servizio foreground e WakeLock.


## Come funziona

All'avvio l'applicazione configura i permessi per l'esecuzione e garantisce che quest'ultima avvenga in background e non si
interrompa.
In seguito scansiona nelle vicinanze tramite BLE per trovare un dispositivo che esponga un `Service UUID` specifico, con cui poi si connette e inizia l'interazione GATT.
Avviata questa interazione, cerca una caratteristica specifica su cui scrivere i dati e fornisce i JSON ottenuti, contenenti questi dati:
- accelerometro
- giroscopio
- timestamp: orario in cui è ottenuto il dato
- batchId: identificativo che accomuna più dati dello stesso batch.

Per l'analisi di questi dati il progetto richiede batch da 400 campioni (snapshot), quindi il batchId serve proprio a permettere al dispositivo che riceve questi dati di ricostruire il pacchetto intero.

Se la connesssione fallisce, dopo un breve delay tutta la sequenza riprende dall'inizio






