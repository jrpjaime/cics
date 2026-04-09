import { Injectable } from '@angular/core';
import { BehaviorSubject, Subject } from 'rxjs';
import { Constants } from '../../global/Constants';

@Injectable({
  providedIn: 'root'
})
export class SharedService {

  private tokenKey = Constants.tokenKey;

  // 1. Variables para el Contexto de Trabajo (RFC con el que se opera)
  private rfcSource = new BehaviorSubject<string>('');
  currentRfc = this.rfcSource.asObservable();

  private curpSource = new BehaviorSubject<string>('');
  currentCurp = this.curpSource.asObservable();

  private nombreSource = new BehaviorSubject<string>('');
  currentNombre = this.nombreSource.asObservable();

  private primerApellidoSource = new BehaviorSubject<string>('');
  currentPrimerApellido = this.primerApellidoSource.asObservable();

  private segundoApellidoSource = new BehaviorSubject<string>('');
  currentSegundoApellido = this.segundoApellidoSource.asObservable();

  private roleSource = new BehaviorSubject<string[]>([]);
  currentRole = this.roleSource.asObservable();

  private registroPatronalSource = new BehaviorSubject<string>('');
  currentRegistroPatronal = this.registroPatronalSource.asObservable();


  // 2. Variables de Identidad (Usuario Autenticado)
  private rfcSesionSource = new BehaviorSubject<string>('');
  currentRfcSesion = this.rfcSesionSource.asObservable();

  private curpSesionSource = new BehaviorSubject<string>('');
  currentCurpSesion = this.curpSesionSource.asObservable();

  private nombreSesionSource = new BehaviorSubject<string>('');
  currentNombreSesion = this.nombreSesionSource.asObservable();

  private primerApellidoSesionSource = new BehaviorSubject<string>('');
  currentPrimerApellidoSesion = this.primerApellidoSesionSource.asObservable();

  private segundoApellidoSesionSource = new BehaviorSubject<string>('');
  currentSegundoApellidoSesion = this.segundoApellidoSesionSource.asObservable();

  private roleSesionSource = new BehaviorSubject<string[]>([]);
  currentRoleSesion = this.roleSesionSource.asObservable();

  private delegacionSesionSource = new BehaviorSubject<string>('');
  currentDelegacionSesion = this.delegacionSesionSource.asObservable();

  private subdelegacionSesionSource = new BehaviorSubject<string>('');
  currentSubdelegacionSesion = this.subdelegacionSesionSource.asObservable();

  private numeroRegistroImssSesionSource = new BehaviorSubject<string>('');
  currentNumeroRegistroImssSesion = this.numeroRegistroImssSesionSource.asObservable();

  private indBajaSesionSource = new BehaviorSubject<boolean>(false);
  currentIndBajaSesion = this.indBajaSesionSource.asObservable();

  private cveIdEstadoCpaSesionSource = new BehaviorSubject<number | null>(null);
  currentCveIdEstadoCpaSesion = this.cveIdEstadoCpaSesionSource.asObservable();

  constructor() {}

  // --- MÉTODOS DE ACTUALIZACIÓN DE CONTEXTO ---
  changeRfc(rfc: string) { this.rfcSource.next(rfc); }
  changeCurp(curp: string) { this.curpSource.next(curp); }
  changeNombre(nombre: string) { this.nombreSource.next(nombre); }
  changePrimerApellido(primerApellido: string) { this.primerApellidoSource.next(primerApellido); }
  changeSegundoApellido(segundoApellido: string) { this.segundoApellidoSource.next(segundoApellido); }
  changeRole(roles: string[]) { this.roleSource.next(roles); }
  changeRegistroPatronal(registroPatronal: string) { this.registroPatronalSource.next(registroPatronal); }

  // --- MÉTODOS DE ACTUALIZACIÓN DE SESIÓN ---
  changeRfcSesion(rfcSesion: string) { this.rfcSesionSource.next(rfcSesion); }
  changeCurpSesion(curpSesion: string) { this.curpSesionSource.next(curpSesion); }
  changeNombreSesion(nombreSesion: string) { this.nombreSesionSource.next(nombreSesion); }
  changePrimerApellidoSesion(primerApellidoSesion: string) { this.primerApellidoSesionSource.next(primerApellidoSesion); }
  changeSegundoApellidoSesion(segundoApellidoSesion: string) { this.segundoApellidoSesionSource.next(segundoApellidoSesion); }
  changeRoleSesion(rolesSesion: string[]) { this.roleSesionSource.next(rolesSesion); }
  changeDelegacionSesion(delegacionSesion: string) { this.delegacionSesionSource.next(delegacionSesion); }
  changeSubdelegacionSesion(subdelegacionSesion: string) { this.subdelegacionSesionSource.next(subdelegacionSesion); }
  changeNumeroRegistroImssSesion(numeroRegistroImssSesion: string) { this.numeroRegistroImssSesionSource.next(numeroRegistroImssSesion); }
  changeIndBajaSesion(indBajaSesion: boolean) { this.indBajaSesionSource.next(indBajaSesion); }
  changeCveIdEstadoCpaSesion(estatus: number | null) { this.cveIdEstadoCpaSesionSource.next(estatus); }

  // --- GETTERS SÍNCRONOS ---
  get currentNumeroRegistroImssSesionValue(): string { return this.numeroRegistroImssSesionSource.getValue(); }
  get currentRfcSesionValue(): string { return this.rfcSesionSource.getValue(); }

  /**
   * INICIALIZACIÓN DE DATOS DESDE EL TOKEN JWT
   * Este método extrae toda la información de seguridad del Hub y el mapeo.
   */
  initializeUserData(): void {
    console.log("INICIO SharedService initializeUserData");
    const token = sessionStorage.getItem(this.tokenKey);

    if (!token) return;

    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      console.log("JWT Payload:", payload);

      // --- CORRECCIÓN AQUÍ: Limpiar el prefijo ROLE_ ---
      let rawRoles: string[] = [];
      if (payload.role) {
          rawRoles = Array.isArray(payload.role) ? payload.role : [payload.role];
      } else if (payload.roles) {
          rawRoles = Array.isArray(payload.roles) ? payload.roles : [payload.roles];
      }

      // Convertimos "ROLE_ADMIN" en "ADMIN" para que coincida con el menú
      const roles = rawRoles.map(r => r.replace('ROLE_', ''));
      // ------------------------------------------------

      const userApi = payload.sub || '';

      // Actualizar observables de sesión con roles limpios
      this.changeRoleSesion(roles);
      this.changeNombreSesion(payload.nombre || userApi);
      this.changeRfcSesion(payload.rfc || '');
      this.changeCurpSesion(payload.curp || '');
      this.changeNumeroRegistroImssSesion(payload.numeroRegistroImss || '');
      this.changeCveIdEstadoCpaSesion(payload.cveIdEstadoCpa || null);

      // Actualizar Contexto de trabajo
      this.changeRole(roles);
      this.changeRfc(payload.rfc || '');
      this.changeNombre(payload.nombre || userApi);

    } catch (error) {
      console.error("Error al decodificar token:", error);
    }
    console.log("TERMINA SharedService initializeUserData");
  }

  // --- EVENTOS DE REINICIO DE FORMULARIOS ---
  private resetModificacionDatosSource = new Subject<void>();
  resetModificacionDatos$ = this.resetModificacionDatosSource.asObservable();
  triggerResetModificacionDatos() {
    this.resetModificacionDatosSource.next();
  }

  private resetSolicitudBajaSource = new Subject<void>();
  resetSolicitudBaja$ = this.resetSolicitudBajaSource.asObservable();
  triggerResetSolicitudBaja() {
    this.resetSolicitudBajaSource.next();
  }


  getRolesSync(): string[] {
  return this.roleSesionSource.getValue();
}
}
