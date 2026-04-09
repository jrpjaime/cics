import { Directive, OnInit } from '@angular/core';
import { SharedService } from '../services/shared.service';

import { AuthService } from '../../core/services/auth.service';
import { Constants } from '../../global/Constants';
import { combineLatest, map, Observable, startWith, Subscription } from 'rxjs';
import { PaginatorState } from '../paginador/PaginatorState';

@Directive()
export class BaseComponent implements OnInit {

  numeroRegistroImssSesion: string = '';
  nombreCompleto$: Observable<string> = new Observable<string>();
  rfc: string = '';


  rfcSesion: string = '';



  nombreSesion: string = '';
  primerApellidoSesion: string = '';
  segundoApellidoSesion: string = '';
  curpSesion: string = '';




  cveIdEstadoCpaSesion: number | null = null; // El ID numérico tal cual (3, 10, etc.)
  indBajaSesion: boolean = false;            // TRUE solo si estatus es 10
  indPendienteBajaSesion: boolean = false;   // TRUE solo si estatus es 3

  protected subs: Subscription[] = [];


  roles: string[] = [];
  indPatron: boolean = false;






  indFase: number = 1;

  desDelegacionSesion: string = '';
  desSubdelegacionSesion: string = '';


/*
  selectedFile: File | null = null; // Variable para almacenar el archivo seleccionado
  fileErrorMessage: string = '';
*/
   readonly PATTERNS = {
      cveRegistroPatronal: '^[A-Za-z0-9]{8}[0-9]{2}[0-9]{1}$',
      cveNss: '^[0-9]{11}$'

    };


  constructor(
     protected sharedService: SharedService) {}









  ngOnInit(): void {
   this.setupUserDataObservers();
  }

  recargaParametros(): void {
    console.log('.........BaseComponent ');
    this.sharedService.initializeUserData();

     // Suscripción al Estatus CPA (Lógica centralizada)
    this.subs.push(
      this.sharedService.currentCveIdEstadoCpaSesion.subscribe(estatus => {
        this.cveIdEstadoCpaSesion = estatus;

        // Separamos las banderas
        this.indBajaSesion = (estatus === 10);          // Baja definitiva
        this.indPendienteBajaSesion = (estatus === 3);  // Trámite de baja pendiente
        console.log('>>> Banderas - indBaja:', this.indBajaSesion, ' | indPendiente:', this.indPendienteBajaSesion);
      })
    );

    this.sharedService.currentRfc.subscribe(rfc => {
      this.rfc = rfc;
      console.log('this.rfc: ', this.rfc);
    });


    this.sharedService.currentRfcSesion.subscribe(rfcSesion => {
      this.rfcSesion = rfcSesion;
    });


    this.sharedService.currentCurpSesion.subscribe(curpSesion => {
      this.curpSesion = curpSesion;
    });


    this.sharedService.currentNombreSesion.subscribe(nombreSesion => {
      this.nombreSesion = nombreSesion;
    });

    this.sharedService.currentPrimerApellidoSesion.subscribe(primerApellidoSesion => {
      this.primerApellidoSesion = primerApellidoSesion;
    });

    this.sharedService.currentSegundoApellidoSesion.subscribe(segundoApellidoSesion => {
      this.segundoApellidoSesion = segundoApellidoSesion;
    });

    this.sharedService.currentRoleSesion.subscribe(roles => {
        this.roles = roles;
        // Usa .includes() para verificar si el usuario tiene el rol de 'Patron'
        if (this.roles.includes(Constants.rolePatron)) {
        this.indPatron = true;
        } else {
        this.indPatron = false;
        }
        console.log('this.roles: ' + this.roles.join(', '));
    });



    this.sharedService.currentNumeroRegistroImssSesion.subscribe(numeroRegistroImssSesion => {
      this.numeroRegistroImssSesion = numeroRegistroImssSesion;
      console.log('this.numeroRegistroImssSesion: ', this.numeroRegistroImssSesion);
    });







    this.sharedService.currentSubdelegacionSesion.subscribe(desDelegacionSesion => {
      this.desDelegacionSesion = desDelegacionSesion;
    });

    this.sharedService.currentDelegacionSesion.subscribe(desSubdelegacionSesion => {
      this.desSubdelegacionSesion = desSubdelegacionSesion;
    });















  }





// Método para formatear la fecha
formatDate(date: string): string {
  if (!date) {
    return ''; // Si la fecha es null o vacía, devuelve una cadena vacía
  }

  const [day, month, year] = date.split('/');
  const formattedDate = new Date(`${year}-${month}-${day}`);
  return formattedDate.toISOString().split('T')[0]; // Devuelve en formato "yyyy-MM-dd"
}







  // Este  método configura un Observable para el nombre completo
  // que se actualizará automáticamente cuando cambie cualquiera de sus partes.
  private setupUserDataObservers(): void {
    this.nombreCompleto$ = combineLatest([
      this.sharedService.currentNombreSesion.pipe(startWith('')), // Asegura un valor inicial
      this.sharedService.currentPrimerApellidoSesion.pipe(startWith('')),
      this.sharedService.currentSegundoApellidoSesion.pipe(startWith(''))
    ]).pipe(
      map(([nombre, primerApellido, segundoApellido]) => {
        let partes: string[] = [];
        if (nombre) partes.push(nombre);
        if (primerApellido) partes.push(primerApellido);
        if (segundoApellido) partes.push(segundoApellido);
        return partes.join(' ').trim();
      })
    );
  }








  get nombreCompletoSync(): string {
      const nombre = this.nombreSesion || '';
      const primerApellido = this.primerApellidoSesion || '';
      const segundoApellido = this.segundoApellidoSesion || '';
      let partes: string[] = [];
      if (nombre) partes.push(nombre);
      if (primerApellido) partes.push(primerApellido);
      if (segundoApellido) partes.push(segundoApellido);
      return partes.join(' ').trim();
  }








  getDatosParaAcuse(): any {
    const datosAcuse = {
      nombreCompleto: this.nombreCompletoSync, // Usar el getter síncrono si necesitas el valor actual
      RFC: this.rfcSesion, // Ya está suscrito en recargaParametros
      CURP: this.curpSesion // Ya está suscrito en recargaParametros
    };
    console.log('Datos para el acuse:', datosAcuse);
    return datosAcuse;
  }



    ngOnDestroy(): void {
    // Aquí es donde el BaseComponent limpiaría sus propias suscripciones
    // Por ejemplo:
    // this.subscriptions.forEach(sub => sub.unsubscribe());
    // console.log('BaseComponent ngOnDestroy executed.');
  }



  protected createPaginator(size: number = 5): PaginatorState {
    return new PaginatorState(size);
  }

}

