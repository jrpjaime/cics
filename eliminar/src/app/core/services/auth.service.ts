import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, Observable, of, tap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { EPs } from '../../global/endPoint';
import { Constants } from '../../global/Constants';
import { ModalService } from '../../shared/services/modal.service';


export interface AuthResponse {
  token: string;
  refreshToken: string;
}


@Injectable({
  providedIn: 'root'
})
export class AuthService {

  private LOGIN_URL = `${environment.seguridadApiUrl}${EPs.oauth.login}`;
  private tokenKey = Constants.tokenKey;

  private REFRESH_URL = `${environment.seguridadApiUrl}${EPs.oauth.refresh}`;
  private refreshTokenKey = Constants.refreshTokenKey;
  private timeoutId:any;



  constructor(
    private httpClient: HttpClient,
    private router: Router,
    private modalService: ModalService
  ) { }

login(username: string, password: string): Observable<any> {
  // El backend espera LoginRequest { username, password }
  return this.httpClient.post<any>(this.LOGIN_URL, { username, password }).pipe(
    tap(response => {
      if (response.token) {
        sessionStorage.setItem(Constants.tokenKey, response.token);
        this.autoRefreshToken();
      }
    }),
    catchError(this.handleError)
  );
}

private handleError(error: HttpErrorResponse) {
  // Simplemente relanza el objeto HttpErrorResponse completo.
  // El componente se encargará de interpretarlo.
  return throwError(() => error);
}

  private setToken(token: string): void {
    sessionStorage.setItem(this.tokenKey, token);
  }

  public getToken(): string | null {
    if(typeof window !== 'undefined'){
      return sessionStorage.getItem(this.tokenKey);
    }else {
      return null;
    }
  }

  private setRefreshToken(token: string): void {
    sessionStorage.setItem(this.refreshTokenKey, token);
  }

  private getRefreshToken(): string | null {
    if(typeof window !== 'undefined'){
      return sessionStorage.getItem(this.refreshTokenKey);
    }else {
      return null;
    }
  }



  refreshToken(): Observable<AuthResponse | null> {
    const refreshToken = this.getRefreshToken();
    console.log("refreshToken: " + refreshToken);
    console.log("this.REFRESH_URL: " + this.REFRESH_URL);

    if (!refreshToken) {
      console.log("El refreshToken es null o undefined, no se hará la petición.");
      // Limpiar y redirigir si no hay refresh token, podría ser una sesión expirada.
      this.logout();
      return of(null);
    }


    const body = {
      refreshToken: refreshToken,

    };

    return this.httpClient.post<AuthResponse>(this.REFRESH_URL, body).pipe(
      tap(response => {
        if (response && response.token) {
          this.setToken(response.token);
          this.setRefreshToken(response.refreshToken);

          // NUEVO: Actualiza el contexto también después de cada refresh.
          this.actualizarContextoDesdeToken(response.token);

          this.autoRefreshToken();
        }
      }),
      catchError((error) => {
        // Si el refresh token falla (p.ej., ha expirado), desloguear al usuario.
        console.error("Error al refrescar el token, cerrando sesión.", error);
        this.logout();
        return throwError(() => error);
      })
    );
  }



    /**
   * Decodifica el token JWT, busca el claim 'registroPatronal' y actualiza
   * el ContextoPatronalService si lo encuentra.
   * @param token El token JWT a procesar.
   */
  actualizarContextoDesdeToken(token: string | null): void {
    if (!token) {
      return;
    }

    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      console.log("Payload del token decodificado:", payload);


    } catch (error) {
      console.error("No se pudo decodificar el token JWT:", error);
    }
  }



  autoRefreshToken(): void {
    // Cancelar cualquier timeout anterior para evitar múltiples ejecuciones
    if (this.timeoutId) {
      clearTimeout(this.timeoutId);
    }

    console.log("autoRefreshToken");
    const token = this.getToken();
    if (!token) {
      return;
    }

    const payload = JSON.parse(atob(token.split('.')[1]));
    const expirationTime = payload.exp * 1000;
    const currentTime = Date.now();
    const timeLeft = expirationTime - currentTime;

    // Renovar 1 minuto antes de la expiración. No renovar si queda menos de eso.
    if (timeLeft <= 60000) {
      console.log("El token expira en menos de un minuto, no se programará la renovación automática.");
      return;
    }

    const refreshTime = timeLeft - 60000;
    console.log(`Próxima renovación de token en ${Math.round(refreshTime / 1000 / 60)} minutos.`);

    this.timeoutId = setTimeout(() => {
      console.log("Ejecutando refreshToken automático...");
      this.refreshToken().subscribe({
        next: () => console.log("Token refrescado automáticamente con éxito."),
        error: err => console.error("Falló la renovación automática del token.", err)
      });
    }, refreshTime);
  }



    isAuthenticated(): boolean {
    const token = this.getToken();
    if (!token) {
      return false;
    }
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      const exp = payload.exp * 1000;
      return Date.now() < exp;
    } catch (e) {
      return false; // Token malformado
    }
  }









  /**
   * Método para inicializar el estado de la sesión al cargar la aplicación.
   * Este método debe ser llamado desde AppComponent.ngOnInit().
   */
  /*
  iniciarYRestaurarSesion(): void {
    console.log("AuthService: Verificando e inicializando sesión...");

    // Comprueba si el usuario tiene un token válido y no expirado.
    if (this.isAuthenticated()) {
      console.log("Sesión válida encontrada. Restaurando contexto y programando refresh...");

      // 1. Obtiene el token actual del sessionStorage.
      const token = this.getToken();

      // 2. Restaura el contexto (registroPatronal) desde ese token.
      this.actualizarContextoDesdeToken(token);

      // 3. Programa la renovación automática para el futuro.
      this.autoRefreshToken();

    } else {
      console.log("No se encontró una sesión válida.");
      // Opcional: Podrías hacer una limpieza por si quedaron datos corruptos.
        this.logout();
    }
  }



    logout(): void {
    console.log("Cerrando sesión...");

    this.modalService.close();


    sessionStorage.removeItem(this.tokenKey);
    sessionStorage.removeItem(this.refreshTokenKey);
    Opcional: sessionStorage.clear();

    // Cancelar el refresco automático programado
    if (this.timeoutId) {
      clearTimeout(this.timeoutId);
    }

    this.router.navigate(['/login']);
  }
    */

iniciarYRestaurarSesion(): void {
  console.log("AuthService: Verificando sesión...");

  if (this.isAuthenticated()) {
    console.log("Sesión válida encontrada. Restaurando contexto...");
    const token = this.getToken();
    this.actualizarContextoDesdeToken(token);
    this.autoRefreshToken();
  } else {
    // CAMBIO CLAVE: Ya no llamamos a this.logout()
    // Solo limpiamos los datos locales por si hay basura de una sesión expirada
    console.log("No se encontró sesión activa o el token expiró. El sistema permanecerá en espera.");
    this.limpiarDatosLocales();
  }
}

// Nuevo método auxiliar para limpiar sin redirigir
private limpiarDatosLocales(): void {
  if (this.timeoutId) {
    clearTimeout(this.timeoutId);
  }
  sessionStorage.removeItem(this.tokenKey);
  sessionStorage.removeItem(this.refreshTokenKey);
  // No pongas router.navigate aquí
}

// Modifica el logout para que use el auxiliar
logout(): void {
  console.log("Cerrando sesión y redirigiendo...");
  this.modalService.close();
  this.limpiarDatosLocales();
  this.router.navigate(['/login']);
}


}
