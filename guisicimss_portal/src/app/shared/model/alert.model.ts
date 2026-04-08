export class Alert {
    constructor(
        public id: string,
        public type?: AlertType,
        public message?: string,
        public autoClose: boolean = true,
        public keepAfterRouteChange: boolean = true,
        public fade: boolean = false,
        public scroll: boolean = false
    ) {}
}

export enum AlertType {
    Success,
    Error,
    Info,
    Warning
}
